package co.orion.billing.application;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.orion.billing.domain.PayoutCalculator;
import co.orion.billing.domain.PayoutCalculator.Fortnight;
import co.orion.shared.observability.JobRunRegistry;

/**
 * El corte quincenal (brief de liquidaciones, paso 3). Corre cada hora y mira si la última quincena
 * cerrada —la del 1 al 15, cerrada a las 00:00 del 16; o la del 16 a fin de mes, cerrada a las 00:00
 * del 1— ya tiene su corte. Si no, lo hace: los borradores de esa quincena. Si ya lo tiene, no hace
 * nada, así que correr de más no cuesta. Lo vigila {@code JobWatchdog}.
 */
@Component
public class PayoutCutJob {

    public static final String JOB = "payout-cut";
    private static final Logger log = LoggerFactory.getLogger(PayoutCutJob.class);

    private final PayoutService payouts;
    private final JobRunRegistry runs;
    private final Clock clock;

    public PayoutCutJob(PayoutService payouts, JobRunRegistry runs, Clock clock) {
        this.payouts = payouts;
        this.runs = runs;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${orion.jobs.payout-cut.interval-ms:3600000}",
            initialDelayString = "${orion.jobs.payout-cut.initial-delay-ms:60000}")
    public void cortar() {
        try {
            Fortnight quincena = PayoutCalculator.latestClosed(clock.instant());
            int creadas = run();
            runs.recordSuccess(JOB, clock.instant(), creadas == 0
                    ? "Quincena del " + quincena.start() + " al " + quincena.end() + ": ya cortada"
                    : creadas + " liquidación(es) de la quincena del " + quincena.start() + " al " + quincena.end());
        } catch (RuntimeException ex) {
            log.error("El corte de liquidaciones falló", ex);
            runs.recordFailure(JOB, clock.instant(), ex.getMessage());
        }
    }

    /** Separado del método programado para poder llamarlo desde un test, con el reloj que el test quiera. */
    public int run() {
        return payouts.runCut(PayoutCalculator.latestClosed(clock.instant()));
    }
}
