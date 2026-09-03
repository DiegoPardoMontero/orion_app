package co.orion.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.orion.scheduling.domain.LearningProgress.Tomada;

/**
 * Las rachas son la única aritmética real del panel del estudiante, y la que más fácil se rompe:
 * semanas que cruzan el año, el corte del lunes, la diferencia entre "va bien" y "se cortó".
 * Todo en hora de Bogotá y con el "ahora" por parámetro, así que no hay nada que congelar.
 */
class LearningProgressTest {

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

    /* ---- Qué cuenta como clase tomada ---- */

    @Test
    void unaClaseCerradaCuenta() {
        Instant fin = enBogota(LocalDate.of(2026, 7, 15), 19);

        assertThat(LearningProgress.cuentaComoTomada(BookingStatus.COMPLETED, fin, fin.plusSeconds(1)))
                .isTrue();
    }

    /** Confirmada y ya terminada: ocurrió, aunque el cierre automático aún no la haya tocado. */
    @Test
    void unaConfirmadaQueYaTerminoCuenta() {
        Instant fin = enBogota(LocalDate.of(2026, 7, 15), 19);

        assertThat(LearningProgress.cuentaComoTomada(BookingStatus.CONFIRMED, fin, fin.plusSeconds(1)))
                .isTrue();
    }

    @Test
    void unaConfirmadaQueTodaviaNoOcurreNoCuenta() {
        Instant fin = enBogota(LocalDate.of(2026, 7, 15), 19);

        assertThat(LearningProgress.cuentaComoTomada(BookingStatus.CONFIRMED, fin, fin.minusSeconds(1)))
                .isFalse();
    }

    /** Contar los no-show inflaría el número justo con las veces que algo salió mal. */
    @Test
    void losNoShowNoCuentan() {
        Instant fin = enBogota(LocalDate.of(2026, 7, 15), 19);
        Instant despues = fin.plusSeconds(1);

        assertThat(LearningProgress.cuentaComoTomada(BookingStatus.NO_SHOW_STUDENT, fin, despues)).isFalse();
        assertThat(LearningProgress.cuentaComoTomada(BookingStatus.NO_SHOW_PROFESSOR, fin, despues)).isFalse();
        assertThat(LearningProgress.cuentaComoTomada(BookingStatus.CANCELLED_BY_STUDENT, fin, despues)).isFalse();
        assertThat(LearningProgress.cuentaComoTomada(BookingStatus.PENDING_PAYMENT, fin, despues)).isFalse();
    }

    /* ---- Minutos ---- */

    @Test
    void losMinutosSeSumanDeLasDuracionesReales() {
        List<Tomada> tomadas = List.of(clase(LocalDate.of(2026, 7, 15)), clase(LocalDate.of(2026, 7, 16)));

        assertThat(LearningProgress.minutosTotales(tomadas)).isEqualTo(120);
    }

    @Test
    void sinClasesNoHayMinutos() {
        assertThat(LearningProgress.minutosTotales(List.of())).isZero();
    }

    /* ---- Rachas ---- */

    @Test
    void sinClasesLasRachasSonCero() {
        LearningProgress.Rachas rachas =
                LearningProgress.rachas(List.of(), ahoraEn(LocalDate.of(2026, 7, 15)));

        assertThat(rachas.actual()).isZero();
        assertThat(rachas.mejor()).isZero();
    }

    @Test
    void variasClasesEnLaMismaSemanaSonUnaSolaSemana() {
        // Lunes 13, miércoles 15 y viernes 17 de julio de 2026: la misma semana.
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 13)),
                clase(LocalDate.of(2026, 7, 15)),
                clase(LocalDate.of(2026, 7, 17)));

        LearningProgress.Rachas rachas = LearningProgress.rachas(tomadas, ahoraEn(LocalDate.of(2026, 7, 17)));

        assertThat(rachas.actual()).isEqualTo(1);
        assertThat(rachas.mejor()).isEqualTo(1);
    }

    @Test
    void tresSemanasSeguidasSonTres() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 1)),
                clase(LocalDate.of(2026, 7, 8)),
                clase(LocalDate.of(2026, 7, 15)));

        LearningProgress.Rachas rachas = LearningProgress.rachas(tomadas, ahoraEn(LocalDate.of(2026, 7, 16)));

        assertThat(rachas.actual()).isEqualTo(3);
        assertThat(rachas.mejor()).isEqualTo(3);
    }

    @Test
    void unaSemanaSaltadaCortaLaRacha() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 1)),
                clase(LocalDate.of(2026, 7, 8)),
                // se salta la del 15
                clase(LocalDate.of(2026, 7, 22)));

        LearningProgress.Rachas rachas = LearningProgress.rachas(tomadas, ahoraEn(LocalDate.of(2026, 7, 23)));

        assertThat(rachas.actual()).isEqualTo(1);
        assertThat(rachas.mejor()).isEqualTo(2);
    }

    /**
     * Quien da clase los martes y mira su panel un lunes no ha roto nada: su racha sigue viva
     * mientras la última semana con clase sea esta o la anterior.
     */
    @Test
    void laRachaSigueVivaSiLaUltimaClaseFueLaSemanaPasada() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 7)),
                clase(LocalDate.of(2026, 7, 14)));

        // Lunes 20 de julio: la última clase fue en la semana del 13.
        LearningProgress.Rachas rachas = LearningProgress.rachas(tomadas, ahoraEn(LocalDate.of(2026, 7, 20)));

        assertThat(rachas.actual()).isEqualTo(2);
    }

    @Test
    void laRachaSeCortaAlPasarDosSemanasSinClase() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 7)),
                clase(LocalDate.of(2026, 7, 14)));

        // Lunes 27: entre la última clase y hoy hay una semana entera vacía.
        LearningProgress.Rachas rachas = LearningProgress.rachas(tomadas, ahoraEn(LocalDate.of(2026, 7, 27)));

        assertThat(rachas.actual()).isZero();
        // Pero lo conseguido no se borra.
        assertThat(rachas.mejor()).isEqualTo(2);
    }

    /** El cambio de año no es un corte: la semana del 29 de diciembre y la del 5 de enero son seguidas. */
    @Test
    void unaRachaPuedeCruzarElAnio() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 12, 30)),
                clase(LocalDate.of(2027, 1, 6)),
                clase(LocalDate.of(2027, 1, 13)));

        LearningProgress.Rachas rachas = LearningProgress.rachas(tomadas, ahoraEn(LocalDate.of(2027, 1, 14)));

        assertThat(rachas.actual()).isEqualTo(3);
    }

    /** El domingo cierra la semana; el lunes abre una nueva. Es donde se equivoca cualquier cálculo. */
    @Test
    void elDomingoYElLunesSiguienteSonSemanasDistintas() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 12)),  // domingo
                clase(LocalDate.of(2026, 7, 13))); // lunes

        LearningProgress.Rachas rachas = LearningProgress.rachas(tomadas, ahoraEn(LocalDate.of(2026, 7, 13)));

        assertThat(rachas.actual()).isEqualTo(2);
        assertThat(rachas.mejor()).isEqualTo(2);
    }

    /** La mejor racha es la mejor de todas, no la última. */
    @Test
    void laMejorRachaSobreviveAUnaCaida() {
        List<Tomada> tomadas = new ArrayList<>();
        for (int semana = 0; semana < 5; semana++) {
            tomadas.add(clase(LocalDate.of(2026, 3, 2).plusWeeks(semana)));
        }
        tomadas.add(clase(LocalDate.of(2026, 7, 13)));

        LearningProgress.Rachas rachas = LearningProgress.rachas(tomadas, ahoraEn(LocalDate.of(2026, 7, 14)));

        assertThat(rachas.actual()).isEqualTo(1);
        assertThat(rachas.mejor()).isEqualTo(5);
    }

    /**
     * Una clase de las 11 de la noche del domingo en Bogotá son las 4 de la mañana del lunes en UTC:
     * si el cálculo usara UTC, la contaría en la semana siguiente y partiría la racha en dos.
     */
    @Test
    void laSemanaSeDecideEnBogotaYNoEnUtc() {
        Instant inicio = enBogota(LocalDate.of(2026, 7, 12), 23);
        List<Tomada> tomadas = List.of(new Tomada(PROFE, inicio, inicio.plusSeconds(3600)));

        assertThat(LearningProgress.porDia(tomadas, LocalDate.of(2026, 1, 1)))
                .containsOnlyKeys(LocalDate.of(2026, 7, 12));
    }

    /* ---- Mapa del año ---- */

    @Test
    void elMapaAgrupaPorDiaYCuentaLasClases() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2026, 7, 15)),
                clase(LocalDate.of(2026, 7, 15)),
                clase(LocalDate.of(2026, 7, 16)));

        assertThat(LearningProgress.porDia(tomadas, LocalDate.of(2026, 1, 1)))
                .containsEntry(LocalDate.of(2026, 7, 15), 2)
                .containsEntry(LocalDate.of(2026, 7, 16), 1);
    }

    @Test
    void elMapaDejaFueraLoAnteriorALaVentana() {
        List<Tomada> tomadas = List.of(
                clase(LocalDate.of(2025, 12, 31)),
                clase(LocalDate.of(2026, 1, 1)));

        assertThat(LearningProgress.porDia(tomadas, LocalDate.of(2026, 1, 1)))
                .containsOnlyKeys(LocalDate.of(2026, 1, 1));
    }
}
