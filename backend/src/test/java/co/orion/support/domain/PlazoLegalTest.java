package co.orion.support.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.shared.time.BusinessZone;

/**
 * Los plazos que fija la ley, comprobados contra fechas reales de un calendario.
 *
 * <p>Clase pura, así que estos 150 ms no levantan nada. Es lo mismo que hace {@code SlotCalculator}
 * y por lo mismo: una regla que solo se puede comprobar levantando media aplicación acaba sin
 * comprobarse.
 */
class PlazoLegalTest {

    /** Devuelve el día (en Bogotá) al que apunta un instante. */
    private static LocalDate diaEnBogota(Instant instante) {
        return ZonedDateTime.ofInstant(instante, BusinessZone.BOGOTA).toLocalDate();
    }

    /** Martes 8 de septiembre de 2026, media mañana en Bogotá. */
    private static final Instant MARTES = ZonedDateTime.of(
            LocalDate.of(2026, 9, 8), java.time.LocalTime.of(10, 0), BusinessZone.BOGOTA).toInstant();

    @Test
    @DisplayName("Diez días hábiles desde un martes caen el martes de dos semanas después")
    void diezDiasHabilesDesdeUnMartes() {
        Instant vence = PlazoLegal.sumandoDiasHabiles(MARTES, 10);

        // 9,10,11 (mié-vie) · 14-18 · 21,22 → martes 22 de septiembre.
        assertThat(diaEnBogota(vence)).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    @DisplayName("Quince días hábiles saltan tres fines de semana")
    void quinceDiasHabiles() {
        Instant vence = PlazoLegal.sumandoDiasHabiles(MARTES, 15);

        assertThat(diaEnBogota(vence)).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    /** Un viernes no da un plazo más corto: el sábado y el domingo no cuentan. */
    @Test
    @DisplayName("Un día hábil desde el viernes es el lunes")
    void unDiaHabilDesdeElViernes() {
        Instant viernes = ZonedDateTime.of(
                LocalDate.of(2026, 9, 11), java.time.LocalTime.of(16, 0), BusinessZone.BOGOTA)
                .toInstant();

        assertThat(diaEnBogota(PlazoLegal.sumandoDiasHabiles(viernes, 1)))
                .isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("Los días calendario sí cuentan sábados y domingos")
    void diasCalendario() {
        Instant vence = PlazoLegal.sumandoDiasCalendario(MARTES, 15);

        assertThat(diaEnBogota(vence)).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    /**
     * Vence al cierre del último día y no a la hora en que llegó: quien escribe a las once de la
     * noche no puede tener un día menos que quien escribe por la mañana.
     */
    @Test
    @DisplayName("El vencimiento cae al final del día, no a la hora de la solicitud")
    void venceAlFinalDelDia() {
        Instant casiMedianoche = ZonedDateTime.of(
                LocalDate.of(2026, 9, 8), java.time.LocalTime.of(23, 30), BusinessZone.BOGOTA)
                .toInstant();

        Instant deLaNoche = PlazoLegal.sumandoDiasHabiles(casiMedianoche, 10);
        Instant deLaManana = PlazoLegal.sumandoDiasHabiles(MARTES, 10);

        assertThat(diaEnBogota(deLaNoche)).isEqualTo(diaEnBogota(deLaManana));
    }

    /** Todo se razona en Bogotá. Un domingo a las 23:00 en Bogotá ya es lunes en UTC. */
    @Test
    @DisplayName("El día se decide en Bogotá, no en UTC")
    void elDiaSeDecideEnBogota() {
        // Domingo 13 de septiembre, 23:00 en Bogotá = lunes 14, 04:00 UTC.
        Instant domingoDeNoche = ZonedDateTime.of(
                LocalDate.of(2026, 9, 13), java.time.LocalTime.of(23, 0), BusinessZone.BOGOTA)
                .toInstant();

        // Si se leyera en UTC arrancaría un lunes y el primer hábil sería el martes.
        assertThat(diaEnBogota(PlazoLegal.sumandoDiasHabiles(domingoDeNoche, 1)))
                .isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    @DisplayName("Las categorías sin plazo legal no fabrican uno")
    void lasCategoriasSinPlazoNoInventan() {
        assertThat(TicketCategory.OTRO.vencimientoDesde(MARTES)).isEmpty();
        assertThat(TicketCategory.PAGO.tienePlazoLegal()).isFalse();
        assertThat(TicketCategory.HABEAS_DATA_RECLAMO.tienePlazoLegal()).isTrue();
    }
}
