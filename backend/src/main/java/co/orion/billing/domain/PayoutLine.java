package co.orion.billing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una línea de una liquidación: una clase (o una cancelación tardía) con su bruto, su comisión
 * congelada y el neto del profe; o un ajuste. Una reserva entra una sola vez como clase: lo garantiza
 * el índice único parcial de la base, no un {@code if}.
 */
@Entity
@Table(name = "payout_lines")
public class PayoutLine {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "payout_id", nullable = false, updatable = false)
    private UUID payoutId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 25, updatable = false)
    private PayoutLineKind kind;

    @Column(name = "booking_id", updatable = false)
    private UUID bookingId;

    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "class_at", updatable = false)
    private Instant classAt;

    @Column(name = "student_label", length = 160, updatable = false)
    private String studentLabel;

    @Column(name = "gross_cop", nullable = false, updatable = false)
    private long grossCop;

    @Column(name = "commission_rate_bps", updatable = false)
    private Integer commissionRateBps;

    @Column(name = "commission_cop", nullable = false, updatable = false)
    private long commissionCop;

    @Column(name = "net_cop", nullable = false, updatable = false)
    private long netCop;

    @Column(name = "description", nullable = false, length = 200, updatable = false)
    private String description;

    protected PayoutLine() {
        // exigido por JPA
    }

    public PayoutLine(UUID payoutId, PayoutCalculator.Line line) {
        this.payoutId = Objects.requireNonNull(payoutId, "payoutId");
        this.kind = line.kind();
        this.bookingId = line.bookingId();
        this.paymentId = line.paymentId();
        this.classAt = line.classAt();
        this.studentLabel = line.studentLabel();
        this.grossCop = line.grossCop();
        this.commissionRateBps = line.commissionRateBps();
        this.commissionCop = line.commissionCop();
        this.netCop = line.netCop();
        this.description = line.description();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPayoutId() {
        return payoutId;
    }

    public PayoutLineKind getKind() {
        return kind;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Instant getClassAt() {
        return classAt;
    }

    public String getStudentLabel() {
        return studentLabel;
    }

    public long getGrossCop() {
        return grossCop;
    }

    public Integer getCommissionRateBps() {
        return commissionRateBps;
    }

    public long getCommissionCop() {
        return commissionCop;
    }

    public long getNetCop() {
        return netCop;
    }

    public String getDescription() {
        return description;
    }
}
