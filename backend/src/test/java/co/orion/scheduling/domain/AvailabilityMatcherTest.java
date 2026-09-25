package co.orion.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La regla parece un solapamiento y no lo es: la franja tiene que dejar caber una clase entera.
 * Aquí está la diferencia, que es lo que separa un filtro útil de uno que manda a la gente a
 * perfiles donde no puede reservar.
 */
class AvailabilityMatcherTest {

    private static final Duration UNA_HORA = Duration.ofHours(1);

    private static boolean cabe(String inicioFranja, String finFranja, String from, String to) {
        return AvailabilityMatcher.cabeUnaClase(
                LocalTime.parse(inicioFranja), LocalTime.parse(finFranja),
                from == null ? null : LocalTime.parse(from),
                to == null ? null : LocalTime.parse(to),
                UNA_HORA);
    }

    @Test
    @DisplayName("Una franja que contiene el rango entero cabe")
    void franjaAmplia() {
        assertThat(cabe("08:00", "22:00", "18:00", "21:00")).isTrue();
    }

    @Test
    @DisplayName("Una franja que cae dentro del rango cabe si dura al menos una clase")
    void franjaDentroDelRango() {
        assertThat(cabe("19:00", "20:00", "18:00", "21:00")).isTrue();
    }

    /** El caso que justifica esta clase: solapa, pero nunca va a producir un cupo. */
    @Test
    @DisplayName("Una franja de media hora NO cabe aunque solape")
    void franjaDeMediaHoraNoCabe() {
        assertThat(cabe("18:00", "18:30", "18:00", "21:00")).isFalse();
    }

    @Test
    @DisplayName("Una intersección de menos de una hora no cabe")
    void interseccionCorta() {
        // La franja va de 20:30 a 23:00 y el rango pide hasta las 21:00: solo media hora en común.
        assertThat(cabe("20:30", "23:00", "18:00", "21:00")).isFalse();
    }

    @Test
    @DisplayName("Una intersección de exactamente una hora sí cabe")
    void interseccionJusta() {
        assertThat(cabe("20:00", "23:00", "18:00", "21:00")).isTrue();
    }

    @Test
    @DisplayName("Franjas que no se tocan no caben")
    void sinInterseccion() {
        assertThat(cabe("08:00", "12:00", "18:00", "21:00")).isFalse();
    }

    @Test
    @DisplayName("Sin límites, basta con que la franja dure una clase")
    void sinLimites() {
        assertThat(cabe("08:00", "09:00", null, null)).isTrue();
        assertThat(cabe("08:00", "08:45", null, null)).isFalse();
    }

    @Test
    @DisplayName("Con solo el límite de abajo, cuenta lo que queda desde ahí")
    void soloDesde() {
        assertThat(cabe("08:00", "12:00", "11:00", null)).isTrue();
        assertThat(cabe("08:00", "12:00", "11:30", null)).isFalse();
    }

    @Test
    @DisplayName("Con solo el límite de arriba, cuenta lo que hay hasta ahí")
    void soloHasta() {
        assertThat(cabe("08:00", "12:00", null, "09:00")).isTrue();
        assertThat(cabe("08:00", "12:00", null, "08:30")).isFalse();
    }

    private static boolean empieza(String inicioFranja, String finFranja, String hora) {
        return AvailabilityMatcher.empiezaALas(LocalTime.parse(inicioFranja), LocalTime.parse(finFranja),
                LocalTime.parse(hora), Duration.ofMinutes(55));
    }

    @Test
    @DisplayName("Una hora exacta cabe si la clase entera queda dentro de la franja")
    void horaExacta() {
        assertThat(empieza("18:00", "21:00", "18:00")).isTrue();
        assertThat(empieza("18:00", "21:00", "20:00")).isTrue();
        // Empieza antes de que abra, o terminaría después de que cierre.
        assertThat(empieza("18:00", "21:00", "17:00")).isFalse();
        assertThat(empieza("18:00", "21:00", "21:00")).isFalse();
        assertThat(empieza("20:45", "21:15", "20:00")).isFalse();
    }

    @Test
    @DisplayName("Una clase que cruzaría la medianoche no cabe")
    void medianoche() {
        assertThat(empieza("22:00", "23:59", "23:30")).isFalse();
    }
}
