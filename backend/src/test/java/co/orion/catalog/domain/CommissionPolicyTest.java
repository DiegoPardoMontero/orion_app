package co.orion.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** La comisión de una reserva y el fin del beneficio del profe fundador, sin Spring ni reloj. */
class CommissionPolicyTest {

    private static final int BASE = 2000;
    private static final Instant INICIO = Instant.parse("2026-07-13T17:00:00Z");
    private static final Instant FIN = Instant.parse("2026-10-13T05:00:00Z");

    @Test
    void quienNoEsFundadorPagaLaBase() {
        assertThat(CommissionPolicy.effectiveRate(BASE, null, INICIO)).isEqualTo(2000);
    }

    @Test
    void elFundadorSinClasesPagadasYaTieneSuComision() {
        var sinEmpezar = new FounderTerms(1500, 3, null, null);

        assertThat(CommissionPolicy.effectiveRate(BASE, sinEmpezar, INICIO.plusSeconds(86_400L * 400)))
                .isEqualTo(1500);
    }

    @Test
    void dentroDelPeriodoVaConLaDeFundador() {
        var activo = new FounderTerms(1500, 3, INICIO, FIN);

        assertThat(CommissionPolicy.effectiveRate(BASE, activo, FIN.minusSeconds(1))).isEqualTo(1500);
    }

    @Test
    void exactamenteAlTerminarYaVaConLaBase() {
        var activo = new FounderTerms(1500, 3, INICIO, FIN);

        assertThat(CommissionPolicy.effectiveRate(BASE, activo, FIN)).isEqualTo(2000);
        assertThat(CommissionPolicy.effectiveRate(BASE, activo, FIN.plusSeconds(3600))).isEqualTo(2000);
    }

    @Test
    void elFinEsLaFechaDeInicioEnBogotaMasTresMesesALasCeroHoras() {
        // 13/07 a las 12:00 de Bogotá → 13/10 a las 00:00 de Bogotá (05:00 UTC).
        assertThat(CommissionPolicy.founderUntil(INICIO, 3)).isEqualTo(FIN);
        // Las 22:00 del 30/09 en Bogotá ya son el 1/10 en UTC: cuenta el día de Bogotá.
        assertThat(CommissionPolicy.founderUntil(Instant.parse("2026-10-01T03:00:00Z"), 3))
                .isEqualTo(Instant.parse("2026-12-30T05:00:00Z"));
    }

    @Test
    void finDeMesYCambioDeAnio() {
        // 31/10 + 3 meses = 31/01 del año siguiente.
        assertThat(CommissionPolicy.founderUntil(Instant.parse("2026-10-31T15:00:00Z"), 3))
                .isEqualTo(Instant.parse("2027-01-31T05:00:00Z"));
        // 30/11 + 3 meses = 28/02: febrero no tiene 30.
        assertThat(CommissionPolicy.founderUntil(Instant.parse("2026-11-30T15:00:00Z"), 3))
                .isEqualTo(Instant.parse("2027-02-28T05:00:00Z"));
    }

    @Test
    void elEstadoDelBeneficio() {
        assertThat(new FounderTerms(1500, 3, null, null).status(INICIO)).isEqualTo(FounderTerms.Status.NOT_STARTED);
        assertThat(new FounderTerms(1500, 3, INICIO, FIN).status(FIN.minusSeconds(1))).isEqualTo(FounderTerms.Status.ACTIVE);
        assertThat(new FounderTerms(1500, 3, INICIO, FIN).status(FIN)).isEqualTo(FounderTerms.Status.ENDED);
    }
}
