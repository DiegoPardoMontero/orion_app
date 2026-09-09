package co.orion.engagement.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.engagement.domain.Achievement;
import co.orion.engagement.domain.AchievementUnlockedEvent;
import co.orion.engagement.domain.StreakProtectedEvent;
import co.orion.engagement.persistence.AchievementRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.messaging.application.NotificationService;
import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/**
 * Avisa de las estrellas encendidas.
 *
 * <p>Dos decisiones sobre cuánto avisar, que son la diferencia entre celebrar y molestar:
 *
 * <ul>
 *   <li><strong>Una sola notificación aunque se enciendan varias.</strong> Tres avisos seguidos se
 *       leen como un fallo, no como una celebración.</li>
 *   <li><strong>Correo solo para los hitos de brillo 3</strong> —medio año seguido y cien clases—.
 *       Un correo por cada estrella es exactamente cómo se le enseña a alguien a ignorar tus
 *       correos.</li>
 * </ul>
 */
@Component
public class AchievementNotifier {

    private static final Logger log = LoggerFactory.getLogger(AchievementNotifier.class);

    private static final String TIPO = "ACHIEVEMENT_UNLOCKED";
    private static final String RUTA = "/logros";
    private static final String TIPO_PROTECCION = "STREAK_PROTECTED";
    /** El mapa de semanas vive en el panel de progreso, no en el cielo de logros. */
    private static final String RUTA_PROGRESO = "/cuenta";

    private final AchievementRepository achievements;
    private final NotificationService notifications;
    private final UserRepository users;
    private final MailTransport mail;

    public AchievementNotifier(AchievementRepository achievements,
                               NotificationService notifications,
                               UserRepository users,
                               MailTransport mail) {
        this.achievements = achievements;
        this.notifications = notifications;
        this.users = users;
        this.mail = mail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AchievementUnlockedEvent event) {
        List<Achievement> encendidos = achievements.findAllById(event.achievementCodes());
        if (encendidos.isEmpty()) {
            return;
        }

        try {
            notifications.create(event.studentId(), TIPO, titulo(encendidos), cuerpo(encendidos), RUTA);
        } catch (RuntimeException ex) {
            log.error("No se pudo notificar los logros de {}", event.studentId(), ex);
        }

        encendidos.stream()
                .filter(a -> a.getGlow() == 3)
                .forEach(logro -> correoDeHito(event.studentId(), logro));
    }

    /**
     * La protección de racha, contada en el momento en que ocurre.
     *
     * <p>Lleva al panel de progreso, que es donde está el mapa de semanas: el aviso explica el
     * dibujo raro y el dibujo confirma el aviso. Y dice lo que la protección NO hace —no cuenta
     * como clase— porque si no, la siguiente pregunta es por qué la racha no subió.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(StreakProtectedEvent event) {
        try {
            notifications.create(event.studentId(), TIPO_PROTECCION,
                    "Protegimos tu racha",
                    "Esta semana no tuviste clase y tu racha sigue en pie. Es una protección al mes "
                            + "y no cuenta como clase: la racha se queda donde estaba, no baja.",
                    RUTA_PROGRESO);
        } catch (RuntimeException ex) {
            log.error("No se pudo avisar de la protección de racha de {}", event.studentId(), ex);
        }
    }

    private String titulo(List<Achievement> encendidos) {
        return encendidos.size() == 1
                ? "Encendiste una estrella"
                : "Encendiste " + encendidos.size() + " estrellas";
    }

    private String cuerpo(List<Achievement> encendidos) {
        return encendidos.stream().map(Achievement::getName)
                .reduce((a, b) -> a + " · " + b)
                .orElse("");
    }

    /**
     * Los dos hitos grandes sí merecen un correo. Un fallo aquí se registra y se traga: la estrella
     * ya está encendida, y eso es lo que importa.
     */
    private void correoDeHito(java.util.UUID studentId, Achievement logro) {
        users.findById(studentId).ifPresent(estudiante -> {
            String nombre = estudiante.getFullName().split(" ")[0];
            String html = """
                    <p>Hola, %s.</p>
                    <p>Acabas de encender <strong>%s</strong>.</p>
                    <p>%s</p>
                    <p>No es poca cosa: de todo lo que se puede conseguir en Orión, este es de los
                    que cuestan. Gracias por seguir viniendo.</p>
                    <p>Un abrazo,<br>El equipo de Orión</p>
                    """.formatted(nombre, logro.getName(), logro.getDescription());
            String texto = """
                    Hola, %s.

                    Acabas de encender %s.
                    %s

                    No es poca cosa: de todo lo que se puede conseguir en Orión, este es de los que
                    cuestan. Gracias por seguir viniendo.

                    Un abrazo,
                    El equipo de Orión
                    """.formatted(nombre, logro.getName(), logro.getDescription());
            try {
                mail.send(OutgoingEmail.plain(estudiante.getEmail(),
                        "Encendiste «" + logro.getName() + "»", texto, html));
            } catch (RuntimeException ex) {
                log.error("No se pudo enviar el correo del hito {} a {}",
                        logro.getCode(), studentId, ex);
            }
        });
    }
}
