package co.orion.billing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.orion.billing.domain.PayoutCalculator.Candidate;
import co.orion.billing.domain.PayoutCalculator.Fortnight;
import co.orion.billing.domain.PayoutCalculator.PendingAdjustment;
import co.orion.billing.domain.PayoutCalculator.Result;
import co.orion.shared.time.BusinessZone;

/** Las reglas del brief de liquidaciones, cada una sola. Sin Spring: corren en milisegundos. */
class PayoutCalculatorTest {

    private static final Duration RECLAMO = Duration.ofHours(24);
    /** El corte del 16 de octubre de 2026 a las 00:00 en Bogotá. */
    private static final Instant CORTE = bogota(2026, 10, 16, 0, 0);

    private static Instant bogota(int anio, int mes, int dia, int hora, int minuto) {
        return LocalDateTime.of(anio, mes, dia, hora, minuto).atZone(BusinessZone.BOGOTA).toInstant();
    }

    /** Una clase de $50.000 con la comisión de hoy (20 %), que terminó en ese instante. */
    private static Candidate clase(Instant fin) {
        return new Candidate(UUID.randomUUID(), UUID.randomUUID(), PayoutLineKind.CLASS, fin.minus(Duration.ofMinutes(55)),
                fin, "Ana R.", 50_000, 2000, 10_000, 40_000, false);
    }

    @Test
    void entraLaClaseCuyoPlazoDeReclamoYaVencio() {
        Candidate vencida = clase(bogota(2026, 10, 14, 18, 55));   // vence el 15 a las 18:55
        Candidate enPlazo = clase(bogota(2026, 10, 15, 18, 55));   // vence el 16 a las 18:55, después del corte

        Result r = PayoutCalculator.calculate(List.of(vencida, enPlazo), List.of(), CORTE, RECLAMO);

        assertThat(r.lines()).extracting(PayoutCalculator.Line::bookingId).containsExactly(vencida.bookingId());
        assertThat(r.deferred()).singleElement()
                .satisfies(d -> assertThat(d.reason()).isEqualTo("En plazo de reclamo hasta el 16 de octubre"));
        assertThat(r.grossCop()).isEqualTo(50_000);
        assertThat(r.commissionCop()).isEqualTo(10_000);
        assertThat(r.netCop()).isEqualTo(40_000);
    }

    @Test
    void unReclamoAbiertoLaDejaParaDespues() {
        Candidate conReclamo = new Candidate(UUID.randomUUID(), UUID.randomUUID(), PayoutLineKind.CLASS,
                bogota(2026, 10, 1, 18, 0), bogota(2026, 10, 1, 18, 55), "Ana R.", 50_000, 2000, 10_000, 40_000, true);

        Result r = PayoutCalculator.calculate(List.of(conReclamo), List.of(), CORTE, RECLAMO);

        assertThat(r.lines()).isEmpty();
        assertThat(r.deferred()).singleElement().satisfies(d -> assertThat(d.reason()).isEqualTo("Tiene un reclamo abierto"));
    }

    /** Pagada con saldo a favor, el valor es del profe que la dictó: se liquida igual. */
    @Test
    void laClasePagadaConSaldoSeLiquidaIgual() {
        Candidate conSaldo = clase(bogota(2026, 10, 10, 9, 55));

        assertThat(PayoutCalculator.calculate(List.of(conSaldo), List.of(), CORTE, RECLAMO).netCop()).isEqualTo(40_000);
    }

    @Test
    void laPruebaGratisNoGeneraLinea() {
        Candidate gratis = new Candidate(UUID.randomUUID(), UUID.randomUUID(), PayoutLineKind.CLASS,
                bogota(2026, 10, 1, 18, 0), bogota(2026, 10, 1, 18, 55), "Ana R.", 0, 2000, 0, 0, false);

        Result r = PayoutCalculator.calculate(List.of(gratis), List.of(), CORTE, RECLAMO);

        assertThat(r.lines()).isEmpty();
        assertThat(r.deferred()).isEmpty();
    }

    /** La comisión es la congelada en cada pago, incluida la de fundador: el motor no la recalcula. */
    @Test
    void conservaLaComisionDeFundador() {
        Candidate fundador = new Candidate(UUID.randomUUID(), UUID.randomUUID(), PayoutLineKind.CLASS,
                bogota(2026, 10, 1, 18, 0), bogota(2026, 10, 1, 18, 55), "Ana R.", 50_000, 1500, 7_500, 42_500, false);

        Result r = PayoutCalculator.calculate(List.of(fundador, clase(bogota(2026, 10, 2, 18, 55))), List.of(), CORTE, RECLAMO);

        assertThat(r.lines()).extracting(PayoutCalculator.Line::commissionRateBps).containsExactly(1500, 2000);
        assertThat(r.commissionCop()).isEqualTo(17_500);
        assertThat(r.netCop()).isEqualTo(82_500);
    }

    @Test
    void laCancelacionTardiaEntraComoLineaPropia() {
        Candidate tardia = new Candidate(UUID.randomUUID(), UUID.randomUUID(), PayoutLineKind.LATE_CANCELLATION,
                bogota(2026, 10, 3, 10, 0), bogota(2026, 10, 3, 10, 55), "Ana R.", 50_000, 2000, 10_000, 40_000, false);

        assertThat(PayoutCalculator.calculate(List.of(tardia), List.of(), CORTE, RECLAMO).lines()).singleElement()
                .satisfies(l -> {
                    assertThat(l.kind()).isEqualTo(PayoutLineKind.LATE_CANCELLATION);
                    assertThat(l.description()).isEqualTo("Cancelación tardía del estudiante");
                });
    }

    /** Un ajuste que se come todo deja la liquidación en negativo: no se paga y el saldo se arrastra. */
    @Test
    void unAjusteMayorQueLoGanadoDejaElNetoNegativo() {
        PendingAdjustment devolucion = new PendingAdjustment(UUID.randomUUID(), UUID.randomUUID(),
                PayoutLineKind.REFUND_ADJUSTMENT, -80_000, "Devolución de la clase del 20 sep");

        Result r = PayoutCalculator.calculate(List.of(clase(bogota(2026, 10, 1, 18, 55))), List.of(devolucion), CORTE, RECLAMO);

        assertThat(r.adjustmentsCop()).isEqualTo(-80_000);
        assertThat(r.netCop()).isEqualTo(-40_000);
        assertThat(r.lines()).hasSize(2);
    }

    @Test
    void losCortesDeFinDeMes() {
        // Febrero no bisiesto: la quincena del 16 al 28 se cierra el 1 de marzo.
        Fortnight febrero = PayoutCalculator.latestClosed(bogota(2027, 3, 1, 0, 0));
        assertThat(febrero.start()).isEqualTo(LocalDate.of(2027, 2, 16));
        assertThat(febrero.end()).isEqualTo(LocalDate.of(2027, 2, 28));
        // Bisiesto.
        assertThat(PayoutCalculator.latestClosed(bogota(2028, 3, 5, 12, 0)).end()).isEqualTo(LocalDate.of(2028, 2, 29));
        // Un mes de 30 y uno de 31.
        assertThat(PayoutCalculator.latestClosed(bogota(2026, 10, 1, 0, 0)).end()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(PayoutCalculator.latestClosed(bogota(2026, 11, 3, 8, 0)).end()).isEqualTo(LocalDate.of(2026, 10, 31));
        // Un minuto antes del corte, la última cerrada es la anterior; y el próximo corte es el 16.
        assertThat(PayoutCalculator.latestClosed(bogota(2026, 10, 15, 23, 59)).end()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(PayoutCalculator.next(bogota(2026, 10, 15, 23, 59)).cutoff()).isEqualTo(CORTE);
        assertThat(PayoutCalculator.latestClosed(CORTE).start()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    /** La fecha comprometida: tercer día hábil desde el corte, saltando el festivo del 16 de noviembre. */
    @Test
    void laFechaDePagoSaltaElFestivo() {
        assertThat(PayoutCalculator.committedPayDate(PayoutCalculator.closedBy(LocalDate.of(2026, 11, 16))))
                .isEqualTo(LocalDate.of(2026, 11, 19));
        // El corte del 1 de octubre de 2026 es jueves: jueves, viernes y lunes 5.
        assertThat(PayoutCalculator.committedPayDate(PayoutCalculator.closedBy(LocalDate.of(2026, 10, 1))))
                .isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void loQueVeElProfeEnClasesPorLiquidar() {
        Instant ahora = bogota(2026, 10, 20, 12, 0);
        assertThat(PayoutCalculator.whenItEnters(clase(bogota(2026, 10, 19, 18, 55)), ahora, RECLAMO))
                .isEqualTo("Entra en el corte del 1 de noviembre");
        assertThat(PayoutCalculator.whenItEnters(clase(bogota(2026, 10, 31, 18, 55)), ahora, RECLAMO))
                .isEqualTo("En plazo de reclamo hasta el 1 de noviembre");
    }

    @Test
    void elEstudianteConLaInicialDelApellido() {
        assertThat(PayoutCalculator.studentLabel("Ana Ramírez")).isEqualTo("Ana R.");
        assertThat(PayoutCalculator.studentLabel("Ana María Ramírez")).isEqualTo("Ana R.");
        assertThat(PayoutCalculator.studentLabel("Juan Carlos Pérez Díaz")).isEqualTo("Juan P.");
        assertThat(PayoutCalculator.studentLabel("Carlos")).isEqualTo("Carlos");
    }
}
