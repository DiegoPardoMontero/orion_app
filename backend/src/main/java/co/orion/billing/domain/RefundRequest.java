package co.orion.billing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import co.orion.shared.error.UnprocessableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una devolución debida al medio de pago original.
 *
 * <p>Existe porque Wompi no expone reembolso por API: el movimiento del dinero lo hace una persona
 * en su panel, y esta fila es lo que impide que se olvide. Lleva su propio vencimiento —15 días
 * calendario desde que se ejerció el derecho, art. 47 de la Ley 1480 con el plazo de la Ley 2439 de
 * 2024— y no se puede cerrar sin la referencia de la transferencia.
 */
@Entity
@Table(name = "refund_requests")
public class RefundRequest {

    public enum Reason {
        /** Derecho de retracto del art. 47. Lo ejerce el estudiante y no se puede negar. */
        RETRACTO,
        /** Una devolución que decide administración por fuera del retracto. */
        ADMIN
    }

    public enum Status { PENDING, PAID }

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 20)
    private Reason reason;

    @Column(name = "amount_cop", nullable = false, updatable = false)
    private long amountCop;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "due_at", nullable = false, updatable = false)
    private Instant dueAt;

    @Column(name = "requested_at", nullable = false, updatable = false, insertable = false)
    private Instant requestedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "wompi_reference", length = 140)
    private String wompiReference;

    @Column(name = "note")
    private String note;

    protected RefundRequest() {
        // exigido por JPA
    }

    public RefundRequest(UUID bookingId, UUID paymentId, UUID studentId, Reason reason,
                         long amountCop, Instant dueAt) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.amountCop = amountCop;
        this.dueAt = Objects.requireNonNull(dueAt, "dueAt");
    }

    /**
     * Cierra la devolución con la constancia de la transferencia.
     *
     * <p>Sin referencia no se cierra. Es la misma regla que rige las liquidaciones y por la misma
     * razón: marcar como pagado sin poder señalar el movimiento convierte el registro en una
     * afirmación, y en una devolución legal la afirmación es justo lo que no basta.
     */
    public void markPaid(String reference, String note, UUID actorId, Instant when) {
        if (status == Status.PAID) {
            throw new UnprocessableException("Esa devolución ya estaba marcada como pagada.");
        }
        String limpia = reference == null ? "" : reference.trim();
        if (limpia.isEmpty()) {
            throw new UnprocessableException(
                    "Escribe la referencia de la devolución que hiciste en Wompi.");
        }
        this.status = Status.PAID;
        this.wompiReference = limpia;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.resolvedBy = actorId;
        this.paidAt = when;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    /** Si ya se pasó el plazo legal sin devolver. Es lo que sanciona la SIC. */
    public boolean isOverdue(Instant now) {
        return status == Status.PENDING && now.isAfter(dueAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public Reason getReason() {
        return reason;
    }

    public long getAmountCop() {
        return amountCop;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getWompiReference() {
        return wompiReference;
    }

    public String getNote() {
        return note;
    }
}
