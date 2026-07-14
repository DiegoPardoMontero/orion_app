package co.orion.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IcsGeneratorTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    /** Lunes 20 jul 2026, 18:00 en Bogotá = 23:00 UTC. */
    private static final Instant STARTS_AT = Instant.parse("2026-07-20T23:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2026-07-21T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-07-14T15:30:00Z");

    private final IcsGenerator generator = new IcsGenerator();

    private String ics() {
        return generator.generate(BOOKING_ID, STARTS_AT, ENDS_AT, NOW,
                "Clase de inglés con María Gómez", "Tu clase en Orión.", "Google Meet");
    }

    @Test
    void hasTheCalendarSkeletonAndThePublishMethod() {
        String ics = ics();

        assertThat(ics).startsWith("BEGIN:VCALENDAR");
        assertThat(ics).contains("VERSION:2.0");
        assertThat(ics).contains("METHOD:PUBLISH");
        assertThat(ics).contains("BEGIN:VEVENT");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
    }

    @Test
    void theUidIdentifiesTheBooking() {
        assertThat(ics()).contains("UID:11111111-2222-3333-4444-555555555555@orion");
    }

    @Test
    void datesAreInUtcBasicFormat() {
        String ics = ics();

        // 18:00 de Bogotá se escribe como 23:00 UTC: el calendario del destinatario lo traduce
        // de vuelta a su zona. Si lo escribiéramos como hora local sin zona, la clase aparecería
        // cinco horas corrida para cualquiera que no esté en Colombia.
        assertThat(ics).contains("DTSTART:20260720T230000Z");
        assertThat(ics).contains("DTEND:20260721T000000Z");
        assertThat(ics).contains("DTSTAMP:20260714T153000Z");
    }

    @Test
    void everyLineEndsWithCrlfAsTheRfcDemands() {
        String ics = ics();

        assertThat(ics.lines().count()).isGreaterThan(8);
        // Ni un solo LF suelto: todos van precedidos de CR.
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void escapesCommasSemicolonsAndNewlinesInTextFields() {
        String ics = generator.generate(BOOKING_ID, STARTS_AT, ENDS_AT, NOW,
                "Clase, con María; y Juan",
                "Primera línea\nSegunda línea",
                "Calle 1, oficina 2");

        // Sin escapar, la coma partiría el campo y el calendario mostraría basura.
        assertThat(ics).contains("SUMMARY:Clase\\, con María\\; y Juan");
        assertThat(ics).contains("DESCRIPTION:Primera línea\\nSegunda línea");
        assertThat(ics).contains("LOCATION:Calle 1\\, oficina 2");
    }

    @Test
    void omitsTheLocationWhenThereIsNone() {
        String ics = generator.generate(BOOKING_ID, STARTS_AT, ENDS_AT, NOW, "Clase", "Detalle", null);

        assertThat(ics).doesNotContain("LOCATION:");
    }
}
