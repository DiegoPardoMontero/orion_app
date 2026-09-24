package co.orion.scheduling.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.scheduling.domain.PerfilPorCompletarEvent;

/**
 * Los recordatorios al profesor aprobado que todavía no recibe estudiantes (24/09/2026: «lo mismo
 * que con la ficha del estudiante»): al día de aprobado, un aviso en la app; al segundo, un correo;
 * al tercero, otro aviso. Y nada más: la franja de Rigel sigue en pantalla hasta que termine, pero
 * la campana y el correo no persiguen para siempre.
 *
 * <p>Comparte la tabla de la ficha ({@code profile_reminders}): un paso es de una persona, sea
 * estudiante o profesor. La transacción va por {@code TransactionTemplate}, como en el de la ficha.
 */
@Component
public class RecordatorioDelPerfil {

    private static final Map<Integer, Duration> DESDE = Map.of(
            1, Duration.ofDays(1), 2, Duration.ofDays(2), 3, Duration.ofDays(3));
    private static final Duration ENTRE_PASOS = Duration.ofHours(20);

    private static final Map<String, String> EN_PALABRAS = Map.of(
            "FOTO", "tu foto", "TITULAR", "tu titular", "DESCRIPCION", "tu descripción", "TARIFA", "tu tarifa",
            "IDIOMAS", "los idiomas que enseñas", "HORARIOS", "tus horarios", "PUBLICAR", "publicar tu perfil");

    private final JdbcTemplate jdbc;
    private final PerfilDelProfesor perfil;
    private final PerfilMailer correo;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate enTransaccion;
    private final Clock clock;

    public RecordatorioDelPerfil(JdbcTemplate jdbc, PerfilDelProfesor perfil, PerfilMailer correo,
                                 ApplicationEventPublisher eventos, PlatformTransactionManager transacciones,
                                 Clock clock) {
        this.jdbc = jdbc;
        this.perfil = perfil;
        this.correo = correo;
        this.eventos = eventos;
        this.enTransaccion = new TransactionTemplate(transacciones);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${orion.jobs.profile-reminder.interval-ms:3600000}")
    public void run() {
        recordar();
    }

    /** Lo que falta, dicho en palabras, en el orden en que se pide. */
    public static List<String> enPalabras(List<String> faltan) {
        return faltan.stream().map(EN_PALABRAS::get).toList();
    }

    /**
     * Publicado y con horarios ya recibe reservas: lo que le falte (la foto, por ejemplo) lo mejora, no
     * lo bloquea, y el recordatorio no le dice que «todavía no recibe estudiantes».
     */
    public static boolean yaRecibe(List<String> faltan) {
        return !faltan.contains("PUBLICAR") && !faltan.contains("HORARIOS");
    }

    /** Adónde lleva el aviso: a los horarios si es lo único que falta, al perfil en lo demás. */
    public static String rutaPara(List<String> faltan) {
        return faltan.equals(List.of("HORARIOS")) ? "/perfil?seccion=horarios" : "/perfil";
    }

    /** @return cuántos recordatorios salieron */
    public int recordar() {
        Instant ahora = clock.instant();
        // Profesores aprobados; el reloj arranca con la aprobación (o el alta, si no hay postulación).
        List<Object[]> candidatos = jdbc.query("""
                select u.id, u.email, u.full_name,
                       coalesce((select max(a.reviewed_at) from teacher_applications a
                                  where a.user_id = u.id and a.status = 'APPROVED'), u.created_at),
                       (select max(step) from profile_reminders r where r.user_id = u.id),
                       (select max(sent_at) from profile_reminders r where r.user_id = u.id)
                  from users u
                 where u.role = 'PROFESSOR' and u.status = 'ACTIVE'
                   and exists (select 1 from teacher_applications a where a.user_id = u.id and a.status = 'APPROVED')
                   and (select count(*) from profile_reminders r where r.user_id = u.id) < 3
                """, (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toInstant(), rs.getObject(5), rs.getTimestamp(6)});
        int enviados = 0;
        for (Object[] c : candidatos) {
            UUID id = (UUID) c[0];
            int paso = (c[4] == null ? 0 : ((Number) c[4]).intValue()) + 1;
            Instant desde = (Instant) c[3];
            Instant anterior = c[5] == null ? null : ((Timestamp) c[5]).toInstant();
            if (desde.plus(DESDE.get(paso)).isAfter(ahora)
                    || (anterior != null && anterior.plus(ENTRE_PASOS).isAfter(ahora))) {
                continue;
            }
            List<String> faltan = perfil.faltan(id);
            if (faltan.isEmpty()) {
                continue;
            }
            Boolean salio = enTransaccion.execute(estado -> {
                int filas = jdbc.update("""
                        insert into profile_reminders (user_id, step, sent_at) values (?, ?, ?)
                        on conflict do nothing
                        """, id, paso, Timestamp.from(ahora));
                if (filas == 0) {
                    return false;
                }
                eventos.publishEvent(new PerfilPorCompletarEvent(id, paso, faltan));
                return true;
            });
            if (Boolean.TRUE.equals(salio)) {
                enviados++;
                if (paso == 2) {
                    correo.recordar((String) c[1], (String) c[2], enPalabras(faltan), rutaPara(faltan), yaRecibe(faltan));
                }
            }
        }
        return enviados;
    }
}
