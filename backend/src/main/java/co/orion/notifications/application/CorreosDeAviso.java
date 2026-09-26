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

import co.orion.billing.application.PayoutViews;
import co.orion.billing.domain.PayoutDetailsChangedEvent;
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
    private final PayoutViews views;

    public CorreosDeAviso(MailTransport transport, BookingRepository bookings, ReviewRepository reviews,
                          UserRepository users, Clock clock, @Value("${orion.app.base-url}") String baseUrl,
                          PayoutViews views) {
        this.transport = transport;
        this.bookings = bookings;
        this.reviews = reviews;
        this.users = users;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.views = views;
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
            String cuando = FechasEnPalabras.cuandoConFranja(b.getStartsAt(), b.getEndsAt(), clock.instant());
            enviar(estudiante.get(), "Tu clase con " + primer(profe.get()) + " es " + cuando,
                    "Tu clase con " + profe.get().getFullName() + " es " + cuando + " (hora de Colombia). Dura "
                            + FechasEnPalabras.duracion(b.getStartsAt(), b.getEndsAt()) + ".",
                    "Entras desde «Mis clases» unos minutos antes. Si al final no puedes ir, cancélala con tiempo:"
                            + " así el cupo le sirve a otra persona.",
                    "Ver mi clase", enlace);
            enviar(profe.get(), "Tu clase con " + primer(estudiante.get()) + " es " + cuando,
                    "Tu clase con " + estudiante.get().getFullName() + " es " + cuando + " (hora de Colombia). Dura "
                            + FechasEnPalabras.duracion(b.getStartsAt(), b.getEndsAt()) + ".",
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

    /**
     * El pago de una liquidación, con su comprobante en el cuerpo (brief de liquidaciones, paso 5): el
     * mismo contenido que la página imprimible, para que el profe lo tenga aunque no entre a Orión.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PayoutPaidEvent event) {
        users.findById(event.professorId()).ifPresent(u -> {
            PayoutViews.Receipt r = views.receipt(event.payoutId());
            String periodo = FechasEnPalabras.periodo(r.periodStart(), r.periodEnd());
            String hola = "Hola, " + primer(u) + ":";
            StringBuilder texto = new StringBuilder(hola).append("\n\nTe transferimos ")
                    .append(FechasEnPalabras.pesos(r.netCop())).append(" por tus clases del ").append(periodo).append(".\n\n");
            StringBuilder filas = new StringBuilder();
            for (PayoutViews.ReceiptLine l : r.lines()) {
                String detalle = l.commissionRateBps() != null
                        ? FechasEnPalabras.pesos(l.grossCop()) + " − " + (l.commissionRateBps() / 100) + " % = "
                                + FechasEnPalabras.pesos(l.netCop())
                        : FechasEnPalabras.pesos(l.netCop());
                String quien = l.studentLabel() != null ? " · " + l.studentLabel() : "";
                texto.append("- ").append(l.description()).append(quien).append(": ").append(detalle).append("\n");
                filas.append("<tr><td style=\"padding:4px 8px 4px 0\">").append(esc(l.description() + quien))
                        .append("</td><td style=\"padding:4px 0;text-align:right\">").append(esc(detalle)).append("</td></tr>");
            }
            String totales = "Recibido en tu nombre: " + FechasEnPalabras.pesos(r.grossCop())
                    + ". Comisión de Orión: " + FechasEnPalabras.pesos(r.commissionCop())
                    + (r.adjustmentsCop() != 0 ? ". Ajustes: " + FechasEnPalabras.pesos(r.adjustmentsCop()) : "")
                    + ". Te entregamos: " + FechasEnPalabras.pesos(r.netCop()) + ".";
            String pago = "Transferencia Bre-B del " + FechasEnPalabras.fecha(r.paidOn()) + ", referencia "
                    + r.reference() + ", a la llave " + r.payeeKeyTypeLabel().toLowerCase() + " " + r.payeeMaskedKey()
                    + " a nombre de " + r.payeeHolder() + ".";
            String mandato = "Orión (" + r.mandataryName() + (r.mandataryDocument() != null && !r.mandataryDocument().isBlank()
                    ? ", " + r.mandataryDocument() : "") + ") recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena, menos la comisión.";
            String enlace = baseUrl + "/comprobante/" + r.id();
            texto.append("\n").append(totales).append("\n\n").append(pago).append("\n\n").append(mandato)
                    .append("\n\nComprobante para imprimir o guardar: ").append(enlace).append("\n\nOrión");
            String html = "<p>" + esc(hola) + "</p><p>Te transferimos <strong>" + esc(FechasEnPalabras.pesos(r.netCop()))
                    + "</strong> por tus clases del " + esc(periodo) + ".</p>"
                    + "<table style=\"border-collapse:collapse;width:100%;font-size:14px\">" + filas + "</table>"
                    + "<p>" + esc(totales) + "</p><p>" + esc(pago) + "</p><p style=\"color:#6b5b76\">" + esc(mandato) + "</p>"
                    + "<p><a href=\"" + esc(enlace) + "\">Ver el comprobante</a></p><p>Orión</p>";
            try {
                transport.send(OutgoingEmail.plain(u.getEmail(), "Te pagamos " + FechasEnPalabras.pesos(r.netCop())
                        + " · comprobante de tu liquidación", texto.toString(), html));
            } catch (Exception ex) {
                log.warn("No se pudo enviar el comprobante a {}: {}", u.getEmail(), ex.getMessage());
            }
        });
    }

    /**
     * Cada cambio de los datos de pago, al correo del profe (brief de liquidaciones, regla 13): si no
     * lo hizo él, es la señal de que alguien quiere desviar sus pagos. Con la llave enmascarada.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PayoutDetailsChangedEvent event) {
        String cuando = FechasEnPalabras.dia(event.changedAt()) + " a las " + FechasEnPalabras.hora(event.changedAt());
        users.findById(event.professorId()).ifPresent(u -> enviar(u,
                event.firstTime() ? "Registraste tus datos de pago en Orión" : "Cambiaron tus datos de pago en Orión",
                (event.firstTime() ? "Tus datos para recibir pagos quedaron registrados el " : "Tus datos para recibir pagos cambiaron el ")
                        + cuando + ": llave " + event.keyTypeLabel().toLowerCase() + " " + event.maskedKey()
                        + ", a nombre de " + event.holderName() + ".",
                "Si no fuiste tú, escríbenos de inmediato desde Ayuda en Orión.",
                "Ver mis datos de pago", baseUrl + "/perfil?seccion=pagos"));
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
