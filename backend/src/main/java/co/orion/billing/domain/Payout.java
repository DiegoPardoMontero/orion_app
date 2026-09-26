package co.orion.billing.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * La liquidación de un profe en una quincena: lo que Orión recibió en su nombre, la comisión y lo que
 * le entrega (brief de liquidaciones, paso 3). El sistema la calcula en el corte; el admin la aprueba,
 * transfiere por Bre-B y registra el pago. Pagada, no cambia más: una clase que después se devuelva
 * se descuenta en la siguiente.
 *
 * <p>Cada transición comprueba su estado de partida aquí, y la base lo vuelve a exigir con sus CHECK:
 * una pagada lleva fecha, referencia, llave y titular verificado; una retenida, su motivo.
 */
@Entity
@Table(name = "payouts")
@EntityListeners(AuditingEntityListener.class)
public class Payout {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "cutoff_at", nullable = false, updatable = false)
    private Instant cutoffAt;

    @Column(name = "committed_pay_date", nullable = false, updatable = false)
    private LocalDate committedPayDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "hold_reason", length = 120)
    private String holdReason;

    @Column(name = "gross_cop", nullable = false)
    private long grossCop;

    @Column(name = "commission_cop", nullable = false)
    private long commissionCop;

    @Column(name = "adjustments_cop", nullable = false)
    private long adjustmentsCop;

    /** El neto: lo que se le entrega al profe. */
    @Column(name = "amount_cop", nullable = false)
    private long amountCop;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "reference", length = 140)
    private String reference;

    /** La fecha de la transferencia, como la dice el admin. */
    @Column(name = "paid_on")
    private LocalDate paidOn;

    /** Cuándo se registró el pago en Orión. */
    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_key_type", length = 20)
    private BreBKeyType payeeKeyType;

    @Column(name = "payee_key", length = 100)
    private String payeeKey;

    @Column(name = "payee_holder", length = 150)
    private String payeeHolder;

    @Column(name = "holder_verified", nullable = false)
    private boolean holderVerified;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payout() {
        // exigido por JPA
    }

    public Payout(UUID professorId, PayoutCalculator.Fortnight quincena, LocalDate committedPayDate) {
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.periodStart = quincena.start();
        this.periodEnd = quincena.end();
        this.cutoffAt = quincena.cutoff();
        this.committedPayDate = Objects.requireNonNull(committedPayDate, "committedPayDate");
        this.status = PayoutStatus.DRAFT;
    }

    /** Los totales, del calculador. Solo mientras nadie la haya aprobado. */
    public void setTotals(long gross, long commission, long adjustments) {
        requireStatus(PayoutStatus.DRAFT, PayoutStatus.ON_HOLD);
        this.grossCop = gross;
        this.commissionCop = commission;
        this.adjustmentsCop = adjustments;
        this.amountCop = gross - commission + adjustments;
    }

    /** Retenida con su motivo, o de vuelta a borrador si ya no lo hay. Nunca toca una aprobada o pagada. */
    public void applyHold(String reason) {
        requireStatus(PayoutStatus.DRAFT, PayoutStatus.ON_HOLD);
        if (reason == null) {
            this.status = PayoutStatus.DRAFT;
            this.holdReason = null;
        } else {
            this.status = PayoutStatus.ON_HOLD;
            this.holdReason = reason;
        }
    }

    /** En cero o en negativo no se paga: su saldo pasa a la siguiente liquidación. */
    public void carryOver() {
        requireStatus(PayoutStatus.DRAFT, PayoutStatus.ON_HOLD);
        this.status = PayoutStatus.CARRIED_OVER;
        this.holdReason = null;
    }

    public void approve(UUID adminId, Instant ahora) {
        requireStatus(PayoutStatus.DRAFT);
        if (amountCop <= 0) {
            throw new IllegalStateException("Una liquidación en cero o en negativo no se aprueba: se arrastra");
        }
        this.status = PayoutStatus.APPROVED;
        this.approvedAt = Objects.requireNonNull(ahora, "ahora");
        this.approvedBy = adminId;
    }

    /**
     * Registra la transferencia. Exige la referencia y la confirmación del admin de que el nombre que
     * mostró su banco coincide con el titular: sin eso, «pagada» es una afirmación que nadie puede
     * contrastar con el extracto.
     */
    public void markPaid(LocalDate paidOn, String reference, PayoutDestination destino, boolean holderVerified,
                         UUID adminId, Instant ahora) {
        requireStatus(PayoutStatus.APPROVED);
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("La referencia de la transferencia es obligatoria");
        }
        if (!holderVerified) {
            throw new IllegalArgumentException("Confirma que el nombre que mostró tu banco coincide con el titular");
        }
        this.status = PayoutStatus.PAID;
        this.paidOn = Objects.requireNonNull(paidOn, "paidOn");
        this.reference = reference.trim();
        this.payeeKeyType = destino.keyType();
        this.payeeKey = destino.keyValue();
        this.payeeHolder = destino.holderName();
        this.holderVerified = true;
        this.paidBy = adminId;
        this.paidAt = Objects.requireNonNull(ahora, "ahora");
    }

    public boolean isEditable() {
        return status == PayoutStatus.DRAFT || status == PayoutStatus.ON_HOLD;
    }

    private void requireStatus(PayoutStatus... permitidos) {
        for (PayoutStatus s : permitidos) {
            if (status == s) {
                return;
            }
        }
        throw new IllegalStateException("Una liquidación " + status.etiqueta().toLowerCase() + " no admite este cambio");
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfessorId() {
        return professorId;
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

    public LocalDate getCommittedPayDate() {
        return committedPayDate;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public long getGrossCop() {
        return grossCop;
    }

    public long getCommissionCop() {
        return commissionCop;
    }

    public long getAdjustmentsCop() {
        return adjustmentsCop;
    }

    public long getAmountCop() {
        return amountCop;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getReference() {
        return reference;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public BreBKeyType getPayeeKeyType() {
        return payeeKeyType;
    }

    public String getPayeeKey() {
        return payeeKey;
    }

    public String getPayeeHolder() {
        return payeeHolder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
