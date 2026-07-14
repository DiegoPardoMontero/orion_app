package co.orion.notifications.application;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.stereotype.Component;

import co.orion.identity.domain.User;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BusinessZone;

/**
 * Redacta los correos con la voz de marca: cercana, clara y positiva. Nada de lenguaje de miedo
 * ni de advertencias en mayúsculas. La hora siempre en Bogotá, y el WhatsApp de la contraparte
 * siempre a un clic — es el canal real por el que se coordinan.
 */
@Component
public class BookingEmailComposer {

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a", ES_CO);

    private final IcsGenerator icsGenerator;
    private final GoogleCalendarLinkBuilder calendarLinks;
    private final Clock clock;

    public BookingEmailComposer(IcsGenerator icsGenerator,
                                GoogleCalendarLinkBuilder calendarLinks,
                                Clock clock) {
        this.icsGenerator = icsGenerator;
        this.calendarLinks = calendarLinks;
        this.clock = clock;
    }

    /** Correo de confirmación para uno de los dos participantes. */
    public BookingEmail confirmation(Booking booking, User recipient, User counterpart, boolean recipientIsStudent) {
        String when = humanWhen(booking);
        String modality = modalityOf(booking);
        String title = recipientIsStudent
                ? "Clase de inglés con " + counterpart.getFullName()
                : "Clase de inglés con " + counterpart.getFullName();

        String details = recipientIsStudent
                ? "Tu clase de inglés en Orión con " + counterpart.getFullName() + "."
                : "Clase de inglés en Orión con " + counterpart.getFullName() + ".";

        String location = booking.getLocationNote() != null ? booking.getLocationNote() : modality;

        String ics = icsGenerator.generate(
                booking.getId(), booking.getStartsAt(), booking.getEndsAt(), clock.instant(),
                title, details, location);

        String calendarLink = calendarLinks.build(
                title, booking.getStartsAt(), booking.getEndsAt(), details, location);

        String subject = recipientIsStudent
                ? "¡Listo! Tu clase con " + counterpart.getFullName() + " quedó agendada"
                : "Nueva clase agendada con " + counterpart.getFullName();

        String greeting = "Hola, " + firstName(recipient) + ".";
        String opening = recipientIsStudent
                ? "Tu clase quedó confirmada. Aquí tienes los detalles:"
                : counterpart.getFullName() + " agendó una clase contigo. Aquí tienes los detalles:";

        String html = """
                <p>%s</p>
                <p>%s</p>
                <ul>
                  <li><strong>Cuándo:</strong> %s</li>
                  <li><strong>Modalidad:</strong> %s</li>
                  %s
                  <li><strong>Con:</strong> %s</li>
                </ul>
                <p>Pueden coordinar los detalles por WhatsApp: <a href="%s">%s</a></p>
                <p><a href="%s">Añadir a Google Calendar</a> — o abre el archivo adjunto para
                guardarla en el calendario que uses.</p>
                <p>¡Nos vemos en clase!<br>El equipo de Orión</p>
                """.formatted(
                greeting,
                opening,
                when,
                modality,
                booking.getLocationNote() != null
                        ? "<li><strong>Dónde:</strong> " + booking.getLocationNote() + "</li>"
                        : "",
                counterpart.getFullName(),
                whatsappLink(counterpart),
                whatsappLabel(counterpart),
                calendarLink);

        String text = """
                %s

                %s

                Cuándo: %s
                Modalidad: %s
                Con: %s
                WhatsApp: %s

                Añadir a Google Calendar: %s

                ¡Nos vemos en clase!
                El equipo de Orión
                """.formatted(greeting, opening, when, modality, counterpart.getFullName(),
                whatsappLink(counterpart), calendarLink);

        return new BookingEmail(recipient.getEmail(), subject, html, text, ics);
    }

    /** Cancelación: sin adjunto. La clase ya no existe, no hay nada que añadir al calendario. */
    public BookingEmail cancellation(Booking booking, User recipient, User counterpart, User cancelledBy) {
        String when = humanWhen(booking);
        String who = cancelledBy.getId().equals(recipient.getId())
                ? "Cancelaste"
                : cancelledBy.getFullName() + " canceló";

        String subject = "Clase cancelada: " + when;
        String reason = booking.getCancellationReason();

        String html = """
                <p>Hola, %s.</p>
                <p>%s la clase del <strong>%s</strong> con %s.</p>
                %s
                <p>Cuando quieras, puedes agendar otra clase desde Orión. Y si necesitan hablarlo,
                aquí está el WhatsApp: <a href="%s">%s</a></p>
                <p>Un abrazo,<br>El equipo de Orión</p>
                """.formatted(
                firstName(recipient),
                who,
                when,
                counterpart.getFullName(),
                reason != null && !reason.isBlank()
                        ? "<p><strong>Motivo:</strong> " + reason + "</p>"
                        : "",
                whatsappLink(counterpart),
                whatsappLabel(counterpart));

        String text = """
                Hola, %s.

                %s la clase del %s con %s.
                %s
                Cuando quieras, puedes agendar otra clase desde Orión.
                WhatsApp: %s

                Un abrazo,
                El equipo de Orión
                """.formatted(firstName(recipient), who, when, counterpart.getFullName(),
                reason != null && !reason.isBlank() ? "Motivo: " + reason + "\n" : "",
                whatsappLink(counterpart));

        return new BookingEmail(recipient.getEmail(), subject, html, text, null);
    }

    /** "mié 15 jul, 08:00 a. m., hora de Bogotá" */
    private String humanWhen(Booking booking) {
        var local = booking.getStartsAt().atZone(BusinessZone.BOGOTA);
        String day = local.getDayOfWeek().getDisplayName(TextStyle.SHORT, ES_CO);
        String month = local.getMonth().getDisplayName(TextStyle.SHORT, ES_CO);
        return "%s %d %s, %s, hora de Bogotá".formatted(
                day, local.getDayOfMonth(), month, TIME.format(local));
    }

    private String modalityOf(Booking booking) {
        return booking.getModality() == BookingModality.VIRTUAL ? "Virtual" : "Presencial";
    }

    private String firstName(User user) {
        return user.getFullName().split(" ")[0];
    }

    /** wa.me exige el número sin "+" ni separadores. */
    private String whatsappLink(User user) {
        String phone = user.getWhatsappPhone();
        if (phone == null || phone.isBlank()) {
            return "https://wa.me/";
        }
        return "https://wa.me/" + phone.replaceAll("[^0-9]", "");
    }

    private String whatsappLabel(User user) {
        String phone = user.getWhatsappPhone();
        return phone == null || phone.isBlank() ? "escríbele por WhatsApp" : phone;
    }
}
