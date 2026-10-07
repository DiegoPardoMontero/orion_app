package co.orion.notifications.application;

import java.time.Clock;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import co.orion.catalog.persistence.LanguageRepository;
import co.orion.identity.domain.User;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.shared.time.FechasEnPalabras;

/**
 * Redacta los correos con la voz de marca: cercana, clara y positiva. Nada de lenguaje de miedo
 * ni de advertencias en mayúsculas. La hora siempre en Bogotá.
 *
 * // D4: el contacto ocurre dentro de Orión (mensajería), no por WhatsApp. Los correos invitan a
 * coordinar en la plataforma; el enlace de la videollamada (cuando la clase es virtual) sigue ahí.
 */
@Component
public class BookingEmailComposer {

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");

    private final IcsGenerator icsGenerator;
    private final GoogleCalendarLinkBuilder calendarLinks;
    private final LanguageRepository languages;
    private final Clock clock;
    private final String baseUrl;

    public BookingEmailComposer(IcsGenerator icsGenerator,
                                GoogleCalendarLinkBuilder calendarLinks,
                                LanguageRepository languages,
                                Clock clock,
                                @Value("${orion.app.base-url}") String baseUrl) {
        this.icsGenerator = icsGenerator;
        this.calendarLinks = calendarLinks;
        this.languages = languages;
        this.clock = clock;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /**
     * La sala es una ruta de la app («/mis-clases/…/aula»), y un correo no sabe de rutas: sin el
     * dominio delante, el botón, el texto plano, el .ics y Google Calendar llevaban a ninguna parte.
     */
    private String absoluto(String enlace) {
        if (enlace == null || enlace.startsWith("http://") || enlace.startsWith("https://")) {
            return enlace;
        }
        return baseUrl + (enlace.startsWith("/") ? enlace : "/" + enlace);
    }

    /**
     * El idioma en palabras ("Inglés"), o null si la reserva no lo tiene — las anteriores a la V20
     * de profesores multi-idioma. Donde no se sabe, no se dice nada: mejor una línea menos que una
     * línea inventada.
     */
    private String languageOf(Booking booking) {
        if (booking.getLanguageCode() == null) {
            return null;
        }
        return languages.findById(booking.getLanguageCode())
                .map(l -> l.getNameEs())
                .orElse(null);
    }

    /** Correo de confirmación para uno de los dos participantes. */
    /**
     * La clase cambió de hora porque la contraparte aceptó la propuesta. Dice la hora vieja y la nueva,
     * y trae el .ics con la nueva: el calendario de quien lo guardó queda al día de una vez.
     */
    public BookingEmail rescheduled(Booking booking, User recipient, User counterpart, boolean recipientIsStudent,
                                    java.time.Instant previousStartsAt) {
        String language = languageOf(booking);
        String que = language == null ? "Clase" : "Clase de " + language.toLowerCase(ES_CO);
        String title = que + " con " + counterpart.getFullName();
        String meetingLink = absoluto(booking.getMeetingLink());
        String location = booking.getLocationNote() != null
                ? booking.getLocationNote()
                : (meetingLink != null ? meetingLink : modalityOf(booking));
        String details = que + " en Orión con " + counterpart.getFullName() + ".";
        String eventDetails = meetingLink != null ? details + " Únete: " + meetingLink : details;

        String ics = icsGenerator.generate(
                booking.getId(), booking.getStartsAt(), booking.getEndsAt(), clock.instant(),
                title, eventDetails, location);
        String calendarLink = calendarLinks.build(
                title, booking.getStartsAt(), booking.getEndsAt(), eventDetails, location);

        String antes = humanWhen(previousStartsAt);
        String ahora = humanWhen(booking);
        String subject = "Tu clase con " + counterpart.getFullName() + " cambió de hora";
        String html = """
                <p>Hola, %s.</p>
                <p>%s aceptó el cambio: la clase ya no es %s.</p>
                <p><strong>Nueva hora:</strong> %s</p>
                %s
                <p><a href="%s">Añadir a Google Calendar</a> — o abre el archivo adjunto para
                actualizar la que ya tenías guardada.</p>
                <p>El equipo de Orión</p>
                """.formatted(
                h(firstName(recipient)),
                h(recipientIsStudent ? "Tu profe" : counterpart.getFullName()),
                h(antes), h(ahora),
                meetingLink != null
                        ? "<p><strong>Sala de la clase:</strong> <a href=\"" + h(meetingLink) + "\">Unirse a la videollamada</a></p>"
                        : "",
                h(calendarLink));
        String text = "Hola, " + firstName(recipient) + ".\n\n"
                + (recipientIsStudent ? "Tu profe" : counterpart.getFullName()) + " aceptó el cambio: la clase ya no es "
                + antes + ".\nNueva hora: " + ahora + "\n"
                + (meetingLink != null ? "Sala de la clase: " + meetingLink + "\n" : "")
                + "\nAñadir a Google Calendar: " + calendarLink + "\n\nEl equipo de Orión";
        return new BookingEmail(recipient.getEmail(), subject, html, text, ics);
    }

    public BookingEmail confirmation(Booking booking, User recipient, User counterpart, boolean recipientIsStudent) {
        String when = humanWhen(booking);
        String modality = modalityOf(booking);
        String language = languageOf(booking);

        // "Clase de inglés" estaba escrito a mano y dejó de ser cierto en cuanto hubo francés.
        // Donde no se sabe el idioma —reservas anteriores a la V20— se dice "Clase" a secas.
        String que = language == null ? "Clase" : "Clase de " + language.toLowerCase(ES_CO);
        String title = que + " con " + counterpart.getFullName();

        String details = recipientIsStudent
                ? "Tu " + que.toLowerCase(ES_CO) + " en Orión con " + counterpart.getFullName() + "."
                : que + " en Orión con " + counterpart.getFullName() + ".";

        String meetingLink = absoluto(booking.getMeetingLink());
        String location = booking.getLocationNote() != null
                ? booking.getLocationNote()
                : (meetingLink != null ? meetingLink : modality);

        // La sala de videollamada va en la descripción del evento (y como enlace en el correo).
        String eventDetails = meetingLink != null ? details + " Únete: " + meetingLink : details;

        String ics = icsGenerator.generate(
                booking.getId(), booking.getStartsAt(), booking.getEndsAt(), clock.instant(),
                title, eventDetails, location);

        String calendarLink = calendarLinks.build(
                title, booking.getStartsAt(), booking.getEndsAt(), eventDetails, location);

        String meetingHtml = meetingLink != null
                ? "<p><strong>Sala de la clase:</strong> <a href=\"" + h(meetingLink)
                        + "\">Unirse a la videollamada</a></p>"
                : "";
        String meetingText = meetingLink != null ? "Sala de la clase: " + meetingLink + "\n" : "";

        // La clase de prueba se dice así, como en la campana: el profe tiene que saber que es la
        // primera, y el estudiante, que es la gratis.
        String clase = booking.isTrial() ? "clase de prueba" : "clase";
        String subject = recipientIsStudent
                ? "¡Listo! Tu " + clase + " con " + counterpart.getFullName() + " quedó agendada"
                : "Nueva " + clase + " agendada con " + counterpart.getFullName();

        String greeting = "Hola, " + firstName(recipient) + ".";
        String opening = recipientIsStudent
                ? "Tu " + clase + " quedó confirmada. Aquí tienes los detalles:"
                : counterpart.getFullName() + " agendó una " + clase + " contigo. Aquí tienes los detalles:";

        String html = """
                <p>%s</p>
                <p>%s</p>
                <ul>
                  <li><strong>Cuándo:</strong> %s</li>
                  <li><strong>Duración:</strong> %s</li>
                  %s
                  <li><strong>Modalidad:</strong> %s</li>
                  %s
                  <li><strong>Con:</strong> %s</li>
                </ul>
                %s
                <p>Coordinen los detalles dentro de Orión, en la sección de Mensajes.</p>
                <p><a href="%s">Añadir a Google Calendar</a> — o abre el archivo adjunto para
                guardarla en el calendario que uses.</p>
                <p>¡Nos vemos en clase!<br>El equipo de Orión</p>
                """.formatted(
                h(greeting),
                h(opening),
                when,
                duration(booking),
                language != null ? "<li><strong>Idioma:</strong> " + h(language) + "</li>" : "",
                modality,
                booking.getLocationNote() != null
                        ? "<li><strong>Dónde:</strong> " + h(booking.getLocationNote()) + "</li>"
                        : "",
                h(counterpart.getFullName()),
                meetingHtml,
                h(calendarLink));

        String text = """
                %s

                %s

                Cuándo: %s
                Duración: %s
                %sModalidad: %s
                Con: %s
                %sCoordinen los detalles dentro de Orión, en la sección de Mensajes.

                Añadir a Google Calendar: %s

                ¡Nos vemos en clase!
                El equipo de Orión
                """.formatted(greeting, opening, when, duration(booking),
                language != null ? "Idioma: " + language + "\n" : "",
                modality, counterpart.getFullName(), meetingText, calendarLink);

        return new BookingEmail(recipient.getEmail(), subject, html, text, ics);
    }

    /**
     * Cancelación: sin adjunto. La clase ya no existe, no hay nada que añadir al calendario.
     *
     * <p>Quién canceló se dice de tres maneras: «Cancelaste…» a quien lo hizo; «María canceló la clase
     * que tenían…» al otro, sin repetir su nombre al final; y «Orión canceló…» a los dos cuando no fue
     * ninguno de ellos (un admin): el nombre de quien lo hizo desde administración no le dice nada a
     * nadie, y ellos tratan con Orión.
     */
    public BookingEmail cancellation(Booking booking, User recipient, User counterpart, User cancelledBy) {
        String when = humanWhen(booking);
        boolean recipientIsStudent = recipient.getId().equals(booking.getStudentId());
        boolean fueOrion = !cancelledBy.getId().equals(booking.getStudentId())
                && !cancelledBy.getId().equals(booking.getProfessorId());

        String subject = "Clase cancelada: " + when;
        String reason = booking.getCancellationReason();
        boolean hayMotivo = reason != null && !reason.isBlank();

        String queHtml;
        String queTexto;
        if (fueOrion) {
            queHtml = "Orión canceló tu clase del <strong>" + h(when) + "</strong> con " + h(counterpart.getFullName()) + ".";
            queTexto = "Orión canceló tu clase del " + when + " con " + counterpart.getFullName() + ".";
        } else if (cancelledBy.getId().equals(recipient.getId())) {
            queHtml = "Cancelaste la clase del <strong>" + h(when) + "</strong> con " + h(counterpart.getFullName()) + ".";
            queTexto = "Cancelaste la clase del " + when + " con " + counterpart.getFullName() + ".";
        } else {
            queHtml = h(cancelledBy.getFullName()) + " canceló la clase que tenían el <strong>" + h(when) + "</strong>.";
            queTexto = cancelledBy.getFullName() + " canceló la clase que tenían el " + when + ".";
        }

        // El profe no agenda: al estudiante se le invita a reservar otra; al profe se le dice que
        // la hora vuelve a estar libre.
        String despues = recipientIsStudent
                ? "Cuando quieras, puedes agendar otra clase desde Orión."
                : "Ese horario vuelve a quedar libre en tu agenda.";
        String hablarlo = "Si necesitan hablarlo, escríbanse dentro de Orión, en Mensajes.";

        String html = """
                <p>Hola, %s.</p>
                <p>%s</p>
                %s
                <p>%s %s</p>
                <p>Un abrazo,<br>El equipo de Orión</p>
                """.formatted(
                h(firstName(recipient)),
                queHtml,
                hayMotivo ? "<p><strong>Motivo:</strong> " + h(reason) + "</p>" : "",
                h(despues), h(hablarlo));

        String text = """
                Hola, %s.

                %s
                %s
                %s %s

                Un abrazo,
                El equipo de Orión
                """.formatted(firstName(recipient), queTexto,
                hayMotivo ? "Motivo: " + reason + "\n" : "", despues, hablarlo);

        return new BookingEmail(recipient.getEmail(), subject, html, text, null);
    }

    /**
     * Todo lo que escribió un usuario —su nombre, el motivo de una cancelación, la nota de lugar—
     * pasa por aquí antes de entrar al HTML. Sin esto, un nombre como {@code <a href=…>} llegaba
     * al buzón del otro como un enlace con nuestro remitente: phishing con la reputación de Orión.
     * Con UTF-8 solo se escapan {@code < > & " '}: las tildes quedan como están.
     */
    private static String h(String texto) {
        return HtmlUtils.htmlEscape(texto, "UTF-8");
    }

    /**
     * "miércoles 15 de julio, de 8:00 a 8:55 AM (hora de Colombia)": con su franja y no con la hora
     * de inicio sola, que hacía parecer que la clase podía durar media hora (Pardo, 25/09/2026). La
     * hora, como en el resto de Orión (AM/PM y no "a. m.").
     */
    private String humanWhen(Booking booking) {
        return FechasEnPalabras.dia(booking.getStartsAt()) + ", "
                + FechasEnPalabras.franja(booking.getStartsAt(), booking.getEndsAt()) + " (hora de Colombia)";
    }

    /** La hora que tenía la clase antes de moverse: solo el inicio, que es lo que la gente recuerda. */
    private String humanWhen(java.time.Instant startsAt) {
        return FechasEnPalabras.dia(startsAt) + " a las " + FechasEnPalabras.hora(startsAt) + " (hora de Colombia)";
    }

    private String duration(Booking booking) {
        return FechasEnPalabras.duracion(booking.getStartsAt(), booking.getEndsAt());
    }

    private String modalityOf(Booking booking) {
        return booking.getModality() == BookingModality.VIRTUAL ? "Virtual" : "Presencial";
    }

    private String firstName(User user) {
        return user.getFullName().split(" ")[0];
    }
}
