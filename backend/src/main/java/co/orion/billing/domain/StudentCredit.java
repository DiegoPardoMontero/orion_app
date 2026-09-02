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
 * Saldo a favor de un estudiante. Es un pasivo de Orión, no un descuento al profesor: cuando se
 * gasta, el profesor cobra su tarifa completa y Orión pone la diferencia.
 *
 * Se consume parcialmente (remaining_cop baja) y en orden FIFO por vencimiento, para que el
 * estudiante gaste primero lo que primero se le vence.
 */
@Entity
@Table(name = "student_credits")
@EntityListeners(AuditingEntityListener.class)
public class StudentCredit {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "amount_cop", nullable = false, updatable = false)
    private long amountCop;

    @Column(name = "remaining_cop", nullable = false)
    private long remainingCop;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 40)
    private CreditReason reason;

    @Column(name = "booking_id", updatable = false)
    private UUID bookingId;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentCredit() {
        // exigido por JPA
    }

    public StudentCredit(UUID studentId,
                         long amountCop,
                         CreditReason reason,
                         UUID bookingId,
                         Instant expiresAt,
                         UUID createdBy) {
        if (amountCop <= 0) {
            throw new IllegalArgumentException("Un crédito de cero o menos no es un crédito");
        }
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.amountCop = amountCop;
        this.remainingCop = amountCop;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.bookingId = bookingId;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public boolean isUsableAt(Instant now) {
        return remainingCop > 0 && (expiresAt == null || expiresAt.isAfter(now));
    }

    /** Gasta hasta {@code wanted} de este crédito y devuelve cuánto se pudo gastar de verdad. */
    public long consumeUpTo(long wanted) {
        long taken = Math.min(wanted, remainingCop);
        this.remainingCop -= taken;
        return taken;
    }

    /** Devuelve al crédito lo que se le había quitado (reserva vencida o cancelada sin pagar). */
    public void restore(long amount) {
        if (amount < 0 || remainingCop + amount > amountCop) {
            throw new IllegalArgumentException("Devolución de crédito inconsistente");
        }
        this.remainingCop += amount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public long getAmountCop() {
        return amountCop;
    }

    public long getRemainingCop() {
        return remainingCop;
    }

    public CreditReason getReason() {
        return reason;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
