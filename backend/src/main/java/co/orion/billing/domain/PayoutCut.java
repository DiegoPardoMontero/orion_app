package co.orion.billing.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un corte que ya corrió: cuándo y cuántas liquidaciones creó. Uno por quincena. */
@Entity
@Table(name = "payout_cuts")
public class PayoutCut {

    @Id
    @Column(name = "period_start", updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "cutoff_at", nullable = false, updatable = false)
    private Instant cutoffAt;

    @Column(name = "ran_at", nullable = false, updatable = false)
    private Instant ranAt;

    @Column(name = "payouts_created", nullable = false, updatable = false)
    private int payoutsCreated;

    protected PayoutCut() {
        // exigido por JPA
    }

    public PayoutCut(PayoutCalculator.Fortnight quincena, Instant ranAt, int payoutsCreated) {
        this.periodStart = quincena.start();
        this.periodEnd = quincena.end();
        this.cutoffAt = quincena.cutoff();
        this.ranAt = ranAt;
        this.payoutsCreated = payoutsCreated;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public Instant getCutoffAt() {
        return cutoffAt;
    }

    public Instant getRanAt() {
        return ranAt;
    }

    public int getPayoutsCreated() {
        return payoutsCreated;
    }
}
