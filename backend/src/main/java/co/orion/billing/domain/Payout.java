package co.orion.billing.domain;

import java.time.Instant;
import java.time.LocalDate;
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
 * Lo que Orión le debe a un profesor por un período. El sistema calcula; una persona transfiere y
 * vuelve a marcarla como pagada con la referencia de la transferencia. No hay dispersión automática
 * en este MVP y eso es deliberado (§4.1 del brief).
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

    @Column(name = "amount_cop", nullable = false, updatable = false)
    private long amountCop;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "reference", length = 140)
    private String reference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payout() {
        // exigido por JPA
    }

    public Payout(UUID professorId, LocalDate periodStart, LocalDate periodEnd, long amountCop) {
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
        this.amountCop = amountCop;
        this.status = PayoutStatus.PENDING;
    }

    /**
     * La referencia es obligatoria: una liquidación marcada como pagada sin número de transferencia
     * es una afirmación que nadie puede verificar contra el extracto del banco.
     */
    public void markPaid(String reference, Instant paidAt) {
        if (status != PayoutStatus.PENDING) {
            throw new IllegalStateException("Solo una liquidación PENDING se puede marcar pagada");
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("La referencia de la transferencia es obligatoria");
        }
        this.status = PayoutStatus.PAID;
        this.reference = reference.trim();
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
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

    public long getAmountCop() {
        return amountCop;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public String getReference() {
        return reference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
