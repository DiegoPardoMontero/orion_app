package co.orion.notifications.application;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Genera el adjunto .ics a mano (RFC 5545 básico). Sin librerías: el formato es simple, y así
 * es 100 % testeable con asserts de string.
 *
 * Los detalles del RFC que muerden si se ignoran:
 *  - las líneas terminan en CRLF, no en LF;
 *  - en los campos de texto hay que escapar la coma, el punto y coma, la barra invertida y los
 *    saltos de línea, o el calendario del destinatario parte el campo por donde no debe;
 *  - DTSTART/DTEND van en UTC con sufijo Z: el calendario ya lo traduce a la zona del usuario.
 */
@Component
public class IcsGenerator {

    private static final String CRLF = "\r\n";

    /** Formato básico UTC del RFC: 20260720T230000Z */
    private static final DateTimeFormatter UTC_BASIC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public String generate(UUID bookingId,
                           Instant startsAt,
                           Instant endsAt,
                           Instant now,
                           String summary,
                           String description,
                           String location) {
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Orion Language Academy//Orion//ES");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:" + bookingId + "@orion");
        line(ics, "DTSTAMP:" + UTC_BASIC.format(now));
        line(ics, "DTSTART:" + UTC_BASIC.format(startsAt));
        line(ics, "DTEND:" + UTC_BASIC.format(endsAt));
        line(ics, "SUMMARY:" + escape(summary));
        line(ics, "DESCRIPTION:" + escape(description));
        if (location != null && !location.isBlank()) {
            line(ics, "LOCATION:" + escape(location));
        }
        line(ics, "END:VEVENT");
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private void line(StringBuilder ics, String content) {
        ics.append(content).append(CRLF);
    }

    /** El orden importa: la barra invertida se escapa primero, o se reescaparían las demás. */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }
}
