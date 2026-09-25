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

    /** Una clase se dice con su franja: sola, una hora parece el comienzo de algo que dura media hora. */
    @Test
    void laFranjaDiceElMeridianoUnaVez() {
        Instant siete = Instant.parse("2026-09-25T00:00:00Z");
        assertThat(FechasEnPalabras.franja(siete, siete.plusSeconds(55 * 60))).isEqualTo("de 7:00 a 7:55 PM");
        Instant onceYMedia = Instant.parse("2026-09-25T16:30:00Z");
        assertThat(FechasEnPalabras.franja(onceYMedia, onceYMedia.plusSeconds(55 * 60)))
                .isEqualTo("de 11:30 AM a 12:25 PM");
        assertThat(FechasEnPalabras.duracion(siete, siete.plusSeconds(55 * 60))).isEqualTo("55 minutos");
    }

    @Test
    void hoyMananaOElDiaConLaFranja() {
        Instant manana = Instant.parse("2026-09-25T12:00:00Z");
        assertThat(FechasEnPalabras.cuandoConFranja(manana, manana.plusSeconds(55 * 60), AHORA))
                .isEqualTo("mañana de 7:00 a 7:55 AM");
        Instant sabado = Instant.parse("2026-09-27T00:00:00Z");
        assertThat(FechasEnPalabras.cuandoConFranja(sabado, sabado.plusSeconds(55 * 60), AHORA))
                .isEqualTo("el sábado 26 de septiembre de 7:00 a 7:55 PM");
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
