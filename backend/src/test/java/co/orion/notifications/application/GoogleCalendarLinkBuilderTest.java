package co.orion.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class GoogleCalendarLinkBuilderTest {

    private static final Instant STARTS_AT = Instant.parse("2026-07-20T23:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-07-21T00:00:00Z");

    private final GoogleCalendarLinkBuilder builder = new GoogleCalendarLinkBuilder();

    @Test
    void buildsATemplateLinkWithTheUtcRange() {
        String link = builder.build("Clase de inglés", STARTS_AT, ENDS_AT, "Detalles", "Google Meet");

        assertThat(link).startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE");
        // Google espera inicio/fin en UTC básico, separados por una barra codificada.
        assertThat(link).contains("&dates=20260720T230000Z%2F20260721T000000Z");
    }

    @Test
    void encodesSpacesAsPercentTwentyAndNotAsPlus() {
        String link = builder.build("Clase de inglés con María", STARTS_AT, ENDS_AT, "Detalle", null);

        // URLEncoder usa "+" para el espacio, que en un query string se interpreta literal.
        assertThat(link).contains("text=Clase%20de%20ingl%C3%A9s%20con%20Mar%C3%ADa");
        assertThat(link).doesNotContain("+");
    }

    @Test
    void omitsTheLocationWhenThereIsNone() {
        String link = builder.build("Clase", STARTS_AT, ENDS_AT, "Detalle", null);

        assertThat(link).doesNotContain("&location=");
    }
}
