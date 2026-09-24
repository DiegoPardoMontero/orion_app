package co.orion.notifications.application;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.billing.domain.PayoutPaidEvent;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.lifecycle.domain.ClassReminderDueEvent;
import co.orion.lifecycle.domain.ClassReminderKind;
import co.orion.reputation.domain.SanctionChangedEvent;
import co.orion.reputation.persistence.ReviewRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;
import co.orion.shared.time.FechasEnPalabras;
import co.orion.support.domain.SupportAnsweredEvent;

/**
 * Los correos que faltaban (24/09/2026). Solo lo que no se puede perder si la persona no abre la app:
 * la clase de mañana, la calificación que sigue pendiente al día siguiente, la respuesta de soporte,
 * cualquier sanción y el pago de una liquidación. El recordatorio de una hora antes NO va por correo: a esa altura un correo
 * llega tarde y cansa; para eso están la campana y el dispositivo.
 *
 * <p>{@code @Async} + {@code AFTER_COMMIT}, como los correos de reserva: un servidor de correo lento
 * nunca hace esperar a nadie, y nunca sale un correo de algo que hizo rollback.
 */
@Component
public class CorreosDeAviso {

    private static final Logger log = LoggerFactory.getLogger(CorreosDeAviso.class);

    private final MailTransport transport;
    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final Clock clock;
    private final String baseUrl;

    public CorreosDeAviso(MailTransport transport, BookingRepository bookings, ReviewRepository reviews,
                          UserRepository users, Clock clock, @Value("${orion.app.base-url}") String baseUrl) {
        this.transport = transport;
        this.bookings = bookings;
        this.reviews = reviews;
        this.users = users;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ClassReminderDueEvent event) {
        if (event.kind() == ClassReminderKind.HOUR_BEFORE) {
            return;
        }
        Optional<Booking> reserva = bookings.findById(event.bookingId());
        if (reserva.isEmpty()) {
            return;
        }
        Booking b = reserva.get();
        Optional<User> estudiante = users.findById(b.getStudentId());
        Optional<User> profe = users.findById(b.getProfessorId());
        if (estudiante.isEmpty() || profe.isEmpty()) {
            return;
        }
        String enlace = baseUrl + "/mis-clases?clase=" + b.getId();
        if (event.kind() == ClassReminderKind.DAY_BEFORE) {
            String cuando = FechasEnPalabras.cuando(b.getStartsAt(), clock.instant());
            enviar(estudiante.get(), "Tu clase con " + primer(profe.get()) + " es " + cuando,
                    "Tu clase con " + profe.get().getFullName() + " es " + cuando + " (hora de Colombia).",
                    "Entras desde «Mis clases» unos minutos antes. Si al final no puedes ir, cancélala con tiempo:"
                            + " así el cupo le sirve a otra persona.",
                    "Ver mi clase", enlace);
            enviar(profe.get(), "Tu clase con " + primer(estudiante.get()) + " es " + cuando,
                    "Tu clase con " + estudiante.get().getFullName() + " es " + cuando + " (hora de Colombia).",
                    "Échale un vistazo a su ficha antes de empezar: ahí está para qué quiere el idioma.",
                    "Ver la clase", enlace);
        } else if (event.kind() == ClassReminderKind.RATE && !reviews.existsByBookingId(b.getId())) {
            enviar(estudiante.get(), "¿Cómo te fue con " + primer(profe.get()) + "?",
                    "Todavía no calificas tu clase del " + FechasEnPalabras.dia(b.getStartsAt()) + " con "
                            + profe.get().getFullName() + ".",
                    "Es un toque: te da +20 puntos y a quien busca profe le ayuda a elegir.",
                    "Calificar mi clase", baseUrl + "/mis-clases?clase=" + b.getId() + "&scope=past");
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SupportAnsweredEvent event) {
        users.findById(event.userId()).ifPresent(u -> enviar(u, "Te respondimos (" + event.code() + ")",
                "Respondimos tu solicitud «" + event.subject() + "».",
                "La respuesta está en Orión. Si no quedó resuelto, contéstanos ahí mismo y seguimos.",
                "Ver la respuesta", baseUrl + "/ayuda/" + event.code()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SanctionChangedEvent event) {
        users.findById(event.professorId()).ifPresent(u -> enviar(u,
                event.enPalabras(),
                event.enPalabras() + ".",
                "Motivo: " + event.reason() + ". Si crees que es un error, escríbenos desde Ayuda.",
                "Ver mi desempeño", baseUrl + "/desempeno"));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PayoutPaidEvent event) {
        users.findById(event.professorId()).ifPresent(u -> enviar(u,
                "Te pagamos " + FechasEnPalabras.pesos(event.amountCop()),
                "Te transferimos " + FechasEnPalabras.pesos(event.amountCop()) + " por tus clases del "
                        + FechasEnPalabras.periodo(event.periodStart(), event.periodEnd()) + ".",
                "Clase por clase, con la comisión de Orión, está en «Mis ganancias».",
                "Ver mis ganancias", baseUrl + "/ganancias"));
    }

    private void enviar(User a, String asunto, String primera, String segunda, String boton, String enlace) {
        String hola = "Hola, " + primer(a) + ":";
        String texto = hola + "\n\n" + primera + "\n\n" + segunda + "\n\n" + boton + ": " + enlace + "\n\nOrión";
        String html = "<p>" + esc(hola) + "</p><p>" + esc(primera) + "</p><p>" + esc(segunda) + "</p>"
                + "<p><a href=\"" + esc(enlace) + "\">" + esc(boton) + "</a></p><p>Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(a.getEmail(), asunto, texto, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar «{}» a {}: {}", asunto, a.getEmail(), ex.getMessage());
        }
    }

    private static String primer(User u) {
        return FechasEnPalabras.primerNombre(u.getFullName());
    }

    private static String esc(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
