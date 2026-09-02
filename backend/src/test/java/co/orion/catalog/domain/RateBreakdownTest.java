package co.orion.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Cálculo puro de comisión. Redondeo HACIA ABAJO al peso; la diferencia queda para el profesor. */
class RateBreakdownTest {

    @Test
    void splitsTheCanonicalExample() {
        RateBreakdown b = RateBreakdown.of(50000, 2000);
        assertThat(b.commissionCop()).isEqualTo(10000);
        assertThat(b.earningsCop()).isEqualTo(40000);
    }

    @Test
    void roundsCommissionDownToThePeso() {
        // 33333 * 2000 / 10000 = 6666.6 -> 6666. El profesor se queda con el 0.6.
        RateBreakdown b = RateBreakdown.of(33333, 2000);
        assertThat(b.commissionCop()).isEqualTo(6666);
        assertThat(b.earningsCop()).isEqualTo(33333 - 6666);
    }

    @Test
    void aSinglePesoRoundsCommissionToZero() {
        RateBreakdown b = RateBreakdown.of(1, 2000);
        assertThat(b.commissionCop()).isZero();
        assertThat(b.earningsCop()).isEqualTo(1);
    }

    @Test
    void aDifferentCommissionRateChangesTheSplit() {
        RateBreakdown b = RateBreakdown.of(50000, 1500);
        assertThat(b.commissionCop()).isEqualTo(7500);
        assertThat(b.earningsCop()).isEqualTo(42500);
    }

    @Test
    void alwaysSumsBackToTheGrossRate() {
        RateBreakdown b = RateBreakdown.of(77777, 2000);
        assertThat(b.commissionCop() + b.earningsCop()).isEqualTo(77777);
    }
}
