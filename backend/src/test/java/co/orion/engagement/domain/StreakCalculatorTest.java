package co.orion.engagement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.orion.engagement.domain.StreakCalculator.EstadoSemana;
import co.orion.scheduling.domain.LearningProgress.Tomada;
import co.orion.shared.time.BusinessZone;

/**
 * La racha con protección. Es la aritmética más delicada del bloque: el corte del lunes, el cambio
 * de mes que renueva la protección, y la regla de que dos semanas vacías seguidas cortan aunque
 * quede protección disponible.
 *
 * <p>Todo con el «ahora» por parámetro, así que no hay reloj que congelar.
 */
class StreakCalculatorTest {

    private static final UUID PROFE = UUID.randomUUID();

    private static Instant enBogota(LocalDate dia, int hora) {
        return ZonedDateTime.of(dia, LocalTime.of(hora, 0), BusinessZone.BOGOTA).toInstant();
    }

    private static Tomada clase(LocalDate dia) {
        return new Tomada(PROFE, enBogota(dia, 18), enBogota(dia, 19));
    }

    private static Instant ahoraEn(LocalDate dia) {
        return enBogota(dia, 12);
    }

    /* ---- Lo básico ---- */

    @Test
    void sinClasesNoHayRacha() {
        var racha = StreakCalculator.calcular(List.of(), Set.of(), ahoraEn(LocalDate.of(2026, 7, 20)));

        assertThat(racha.actual()).isZero();
        assertThat(racha.mejor()).isZero();
        assertThat(racha.semanasProtegidas()).isEmpty();
    }

    @Test
    void tresSemanasSeguidasSonTres() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 6)),
                clase(LocalDate.of(2026, 7, 13)),
                clase(LocalDate.of(2026, 7, 20)));

        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2026, 7, 22)));

        assertThat(racha.actual()).isEqualTo(3);
        assertThat(racha.mejor()).isEqualTo(3);
        assertThat(racha.semanasProtegidas()).isEmpty();
    }

    /** Varias clases en la misma semana son una sola semana: el ritmo es semanal, no por clase. */
    @Test
    void variasClasesEnLaMismaSemanaCuentanUna() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 20)),
                clase(LocalDate.of(2026, 7, 22)),
                clase(LocalDate.of(2026, 7, 24)));

        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2026, 7, 24)));

        assertThat(racha.actual()).isEqualTo(1);
    }

    /**
     * La semana en curso todavía no ha terminado. Que aún no tenga clase no rompe nada: quien da
     * clase los viernes y mira su panel el lunes no ha fallado.
     */
    @Test
    void laSemanaEnCursoSinClaseNoCortaLaRacha() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 6)),
                clase(LocalDate.of(2026, 7, 13)));

        // Lunes 20: la semana en curso está vacía todavía.
        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2026, 7, 20)));

        assertThat(racha.actual()).isEqualTo(2);
        assertThat(racha.semanasProtegidas()).isEmpty();
    }

    /* ---- La protección ---- */

    @Test
    void unaSemanaVaciaConProteccionDisponibleNoCortaLaRacha() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 6)),
                // se salta la semana del 13
                clase(LocalDate.of(2026, 7, 20)));

        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2026, 7, 22)));

        assertThat(racha.actual()).isEqualTo(2);
        assertThat(racha.semanasProtegidas()).containsExactly(LocalDate.of(2026, 7, 13));
    }

    @Test
    void sinProteccionDisponibleLaSemanaVaciaSiCorta() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 6)),
                clase(LocalDate.of(2026, 7, 20)));

        // Julio ya gastó su protección.
        var racha = StreakCalculator.calcular(tomadas, Set.of(LocalDate.of(2026, 7, 1)),
                ahoraEn(LocalDate.of(2026, 7, 22)));

        assertThat(racha.actual()).isEqualTo(1);
        assertThat(racha.semanasProtegidas()).isEmpty();
        // Lo conseguido antes no se borra.
        assertThat(racha.mejor()).isEqualTo(1);
    }

    /** Dos vacías seguidas cortan aunque quedara protección: una pausa es una pausa. */
    @Test
    void dosSemanasVaciasSeguidasCortanAunqueHayaProteccion() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 6)),
                // se saltan las del 13 y el 20
                clase(LocalDate.of(2026, 7, 27)));

        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2026, 7, 29)));

        assertThat(racha.actual()).isEqualTo(1);
        // Solo se gastó la primera; la segunda cortó igual.
        assertThat(racha.semanasProtegidas()).containsExactly(LocalDate.of(2026, 7, 13));
    }

    /**
     * El cambio de mes renueva la protección: es una al mes, no una en la vida.
     *
     * <p>Y aquí se ve la regla de fondo: una semana protegida <strong>puentea</strong> la racha
     * pero no suma. Son cuatro semanas con clase, no seis: contar las vacías sería contar una
     * ausencia como si fuera una clase, justo lo que el copy del diseño prohíbe.
     */
    @Test
    void elCambioDeMesRenuevaLaProteccion() {
        List<Tomada> tomadas = new ArrayList<>();
        tomadas.add(clase(LocalDate.of(2026, 7, 6)));
        // vacía: semana del 13 de julio → protegida con la de julio
        tomadas.add(clase(LocalDate.of(2026, 7, 20)));
        tomadas.add(clase(LocalDate.of(2026, 7, 27)));
        // vacía: semana del 3 de agosto → protegida con la de agosto
        tomadas.add(clase(LocalDate.of(2026, 8, 10)));

        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2026, 8, 12)));

        assertThat(racha.semanasProtegidas())
                .containsExactly(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 8, 3));
        assertThat(racha.actual()).isEqualTo(4);
    }

    /** Dos vacías en el mismo mes: la segunda ya no tiene con qué protegerse. */
    @Test
    void dosVaciasSeparadasEnElMismoMesSoloSalvanUna() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 6)),
                // vacía: 13
                clase(LocalDate.of(2026, 7, 20)),
                // vacía: 27
                clase(LocalDate.of(2026, 8, 3)));

        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2026, 8, 5)));

        assertThat(racha.semanasProtegidas()).containsExactly(LocalDate.of(2026, 7, 13));
        // La del 27 cortó: desde ahí solo cuenta la del 3 de agosto.
        assertThat(racha.actual()).isEqualTo(1);
        // La mejor fue 6-jul + 20-jul: dos semanas con clase, puenteadas por la protección.
        assertThat(racha.mejor()).isEqualTo(2);
    }

    /** La mejor marca sobrevive a una caída. Es lo que queda cuando la actual se pierde. */
    @Test
    void laMejorMarcaSobreviveALaCaida() {
        List<Tomada> tomadas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tomadas.add(clase(LocalDate.of(2026, 3, 2).plusWeeks(i)));
        }
        tomadas.add(clase(LocalDate.of(2026, 7, 20)));

        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2026, 7, 22)));

        assertThat(racha.actual()).isEqualTo(1);
        assertThat(racha.mejor()).isEqualTo(6);
    }

    /** Una racha puede cruzar el año sin romperse. */
    @Test
    void laRachaCruzaElAnio() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 12, 28)),
                clase(LocalDate.of(2027, 1, 4)),
                clase(LocalDate.of(2027, 1, 11)));

        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2027, 1, 13)));

        assertThat(racha.actual()).isEqualTo(3);
    }

    /**
     * Una clase del domingo a las 11 de la noche en Bogotá son las 4 de la mañana del lunes en UTC.
     * Si el cálculo usara UTC la contaría en la semana siguiente y partiría la racha en dos.
     */
    @Test
    void laSemanaSeDecideEnBogotaYNoEnUtc() {
        Instant domingoTarde = enBogota(LocalDate.of(2026, 7, 19), 23);
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 13)),
                new Tomada(PROFE, domingoTarde, domingoTarde.plusSeconds(3600)));

        // Las dos caen en la MISMA semana (la del 13), así que la racha es de 1, no de 2.
        var racha = StreakCalculator.calcular(tomadas, Set.of(), ahoraEn(LocalDate.of(2026, 7, 20)));

        assertThat(racha.actual()).isEqualTo(1);
    }

    /* ---- El mapa de constancia ---- */

    @Test
    void elMapaDevuelveLasUltimasSemanasEnOrden() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 13)),
                clase(LocalDate.of(2026, 7, 20)));

        var mapa = StreakCalculator.mapa(tomadas, Set.of(), 4, ahoraEn(LocalDate.of(2026, 7, 22)));

        assertThat(mapa).hasSize(4);
        assertThat(mapa.get(3).weekStart()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(mapa.get(3).estado()).isEqualTo(EstadoSemana.CUMPLIDA);
        assertThat(mapa.get(2).estado()).isEqualTo(EstadoSemana.CUMPLIDA);
        assertThat(mapa.get(1).estado()).isEqualTo(EstadoSemana.VACIA);
    }

    @Test
    void unaSemanaProtegidaSeMarcaComoTalYNoComoVacia() {
        List<Tomada> tomadas = List.of(clase(LocalDate.of(2026, 7, 20)));

        var mapa = StreakCalculator.mapa(tomadas, Set.of(LocalDate.of(2026, 7, 13)), 3,
                ahoraEn(LocalDate.of(2026, 7, 22)));

        assertThat(mapa.get(1).estado()).isEqualTo(EstadoSemana.PROTEGIDA);
    }

    /** La semana en curso sin clase está «en curso», no vacía: todavía se puede cumplir. */
    @Test
    void laSemanaEnCursoSinClaseEstaEnCurso() {
        List<Tomada> tomadas = List.of(clase(LocalDate.of(2026, 7, 13)));

        var mapa = StreakCalculator.mapa(tomadas, Set.of(), 2, ahoraEn(LocalDate.of(2026, 7, 20)));

        assertThat(mapa.get(1).estado()).isEqualTo(EstadoSemana.EN_CURSO);
    }
}
