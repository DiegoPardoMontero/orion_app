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
 * El libro contable de una clase: cuánto valía, cuánto se pagó con crédito, cuánto cobró la
 * pasarela, cuánto retiene Orión y cuánto se le debe al profesor. Una fila por reserva.
 *
 * Como el resto de módulos, las referencias a usuarios y a la reserva son UUIDs planos: la
 * integridad la garantizan las FK de la base, no el grafo de objetos.
 *
 * Las cifras se fijan al crear el pago y no vuelven a cambiar: si el profesor sube su tarifa
 * mañana, la reserva de hoy conserva la de hoy. Lo único mutable es el estado y sus marcas de tiempo.
 */
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    /** Precio de la clase. Sobre este número se calcula la comisión, no sobre lo cobrado. */
    @Column(name = "amount_cop", nullable = false, updatable = false)
    private long amountCop;

    @Column(name = "credit_applied_cop", nullable = false, updatable = false)
    private long creditAppliedCop;

    /** Lo que de verdad va a la pasarela: amount − crédito. Si es 0, no hay checkout. */
    @Column(name = "charged_cop", nullable = false, updatable = false)
    private long chargedCop;

    @Column(name = "commission_rate_bps", nullable = false, updatable = false)
    private int commissionRateBps;

    @Column(name = "commission_cop", nullable = false, updatable = false)
    private long commissionCop;

    @Column(name = "professor_earnings_cop", nullable = false, updatable = false)
    private long professorEarningsCop;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "provider", length = 20)
    private String provider;

    /** Id de la transacción en la pasarela. Único por proveedor: es la clave de conciliación. */
    @Column(name = "provider_reference", length = 140)
    private String providerReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
        // exigido por JPA
    }

    public Payment(UUID bookingId,
                   UUID studentId,
                   UUID professorId,
                   long amountCop,
                   long creditAppliedCop,
                   int commissionRateBps,
                   long commissionCop) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        if (amountCop < 0 || creditAppliedCop < 0 || creditAppliedCop > amountCop) {
            throw new IllegalArgumentException("Importes inconsistentes en el pago");
        }
        this.amountCop = amountCop;
        this.creditAppliedCop = creditAppliedCop;
        this.chargedCop = amountCop - creditAppliedCop;
        this.commissionRateBps = commissionRateBps;
        this.commissionCop = commissionCop;
        this.professorEarningsCop = amountCop - commissionCop;
        this.status = PaymentStatus.PENDING;
    }

    /** Nada que cobrar: el crédito del estudiante cubrió la clase entera. */
    public boolean isFullyCoveredByCredit() {
        return chargedCop == 0;
    }

    /**
     * La pasarela aprobó (o el crédito lo cubrió todo). El dinero queda RETENIDO: el profesor
     * todavía no se lo ganó, eso ocurre cuando la clase se dicta.
     */
    public void markPaid(String provider, String providerReference, Instant paidAt) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Solo un pago PENDING se puede marcar pagado");
        }
        this.status = PaymentStatus.PAID;
        this.provider = provider;
        this.providerReference = providerReference;
        this.paidAt = Objects.requireNonNull(paidAt, "paidAt");
    }

    /** La clase se dictó: el profesor se ganó la plata y el pago ya puede entrar en una liquidación. */
    public void release(Instant releasedAt) {
        if (status != PaymentStatus.PAID) {
            throw new IllegalStateException("Solo un pago PAID se puede liberar");
        }
        this.status = PaymentStatus.RELEASED;
        this.releasedAt = Objects.requireNonNull(releasedAt, "releasedAt");
    }

    /** La clase no ocurrió por causa del profesor o de Orión: el estudiante recupera su valor. */
    public void refund(Instant refundedAt) {
        if (status != PaymentStatus.PAID) {
            throw new IllegalStateException("Solo un pago PAID se puede devolver");
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = Objects.requireNonNull(refundedAt, "refundedAt");
    }

    /** La pasarela rechazó, o la reserva venció antes de pagarse. Nunca hubo plata. */
    public void cancel() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Solo un pago PENDING se puede cancelar");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    /** Guarda la referencia de la pasarela antes de saber el desenlace (conciliación de PENDING). */
    public void attachProviderReference(String provider, String providerReference) {
        this.provider = provider;
        this.providerReference = providerReference;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public long getAmountCop() {
        return amountCop;
    }

    public long getCreditAppliedCop() {
        return creditAppliedCop;
    }

    public long getChargedCop() {
        return chargedCop;
    }

    public int getCommissionRateBps() {
        return commissionRateBps;
    }

    public long getCommissionCop() {
        return commissionCop;
    }

    public long getProfessorEarningsCop() {
        return professorEarningsCop;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
