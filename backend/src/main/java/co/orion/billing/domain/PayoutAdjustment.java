package co.orion.billing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un ajuste que espera el próximo corte del profe: una devolución sobre una clase que ya se le pagó
 * (en negativo), o el saldo de una liquidación que no se pagó. Nunca se toca una liquidación pagada:
 * lo que cambia después entra en la siguiente (brief de liquidaciones, reglas 9 y 10).
 */
@Entity
@Table(name = "payout_adjustments")
@EntityListeners(AuditingEntityListener.class)
public class PayoutAdjustment {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "booking_id", updatable = false)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 25, updatable = false)
    private PayoutLineKind kind;

    @Column(name = "amount_cop", nullable = false, updatable = false)
    private long amountCop;

    @Column(name = "description", nullable = false, length = 200, updatable = false)
    private String description;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "applied_payout_id")
    private UUID appliedPayoutId;

    protected PayoutAdjustment() {
        // exigido por JPA
    }

    public PayoutAdjustment(UUID professorId, UUID bookingId, PayoutLineKind kind, long amountCop, String description) {
        if (kind != PayoutLineKind.REFUND_ADJUSTMENT && kind != PayoutLineKind.CARRY_OVER) {
            throw new IllegalArgumentException("Un ajuste es una devolución o un arrastre: " + kind);
        }
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.bookingId = bookingId;
        this.kind = kind;
        this.amountCop = amountCop;
        this.description = Objects.requireNonNull(description, "description");
    }

    public void applyTo(UUID payoutId) {
        this.appliedPayoutId = payoutId;
    }

    public PayoutCalculator.PendingAdjustment pending() {
        return new PayoutCalculator.PendingAdjustment(id, bookingId, kind, amountCop, description);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public UUID getAppliedPayoutId() {
        return appliedPayoutId;
    }
}
