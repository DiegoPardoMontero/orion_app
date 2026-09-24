package co.orion.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class FechasEnPalabrasTest {

    /** Jueves 24 de septiembre de 2026, 3:00 PM en Bogotá. */
    private static final Instant AHORA = Instant.parse("2026-09-24T20:00:00Z");

    @Test
    void hoyMananaOElDia() {
        assertThat(FechasEnPalabras.cuando(Instant.parse("2026-09-25T00:00:00Z"), AHORA)).isEqualTo("hoy a las 7:00 PM");
        assertThat(FechasEnPalabras.cuando(Instant.parse("2026-09-25T12:00:00Z"), AHORA)).isEqualTo("mañana a las 7:00 AM");
        assertThat(FechasEnPalabras.cuando(Instant.parse("2026-09-27T00:00:00Z"), AHORA))
                .isEqualTo("el sábado 26 de septiembre a las 7:00 PM");
    }

    @Test
    void pesosYPeriodos() {
        assertThat(FechasEnPalabras.pesos(180_000)).isEqualTo("$ 180.000");
        assertThat(FechasEnPalabras.periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)))
                .isEqualTo("1 al 15 de septiembre");
        assertThat(FechasEnPalabras.periodo(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 3)))
                .isEqualTo("28 de agosto al 3 de septiembre");
    }
}
