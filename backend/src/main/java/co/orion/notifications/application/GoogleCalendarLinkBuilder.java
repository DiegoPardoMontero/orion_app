package co.orion.notifications.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/**
 * Link "Añadir a Google Calendar" que precarga el evento. No sincroniza nada: abre el formulario
 * de Google con los datos ya puestos. La sincronización real con la API de Google llega en el MVP 2.
 */
@Component
public class GoogleCalendarLinkBuilder {

    private static final String BASE = "https://calendar.google.com/calendar/render?action=TEMPLATE";

    private static final DateTimeFormatter UTC_BASIC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public String build(String title, Instant startsAt, Instant endsAt, String details, String location) {
        StringBuilder link = new StringBuilder(BASE);
        link.append("&text=").append(encode(title));
        // Google espera el rango como inicioUTC/finUTC en formato básico.
        link.append("&dates=")
                .append(UTC_BASIC.format(startsAt))
                .append("%2F")
                .append(UTC_BASIC.format(endsAt));
        link.append("&details=").append(encode(details));
        if (location != null && !location.isBlank()) {
            link.append("&location=").append(encode(location));
        }
        return link.toString();
    }

    /**
     * URLEncoder codifica el espacio como "+", que es correcto para un formulario pero no para
     * un query string: aquí tiene que ser %20.
     */
    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
