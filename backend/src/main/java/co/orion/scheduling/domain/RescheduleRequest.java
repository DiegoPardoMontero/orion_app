package co.orion.scheduling.domain;

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
 * Una propuesta para mover una clase. No la mueve: la propone.
 *
 * Antes el estudiante reprogramaba solo y el profesor se enteraba al mirar su agenda. Una clase es
 * un acuerdo entre dos personas y moverla también: quien propone escoge un cupo libre del profesor,
 * y la contraparte acepta o propone otro.
 *
 * El horario propuesto se guarda aquí y NO se toca la reserva hasta que alguien acepte. Si el cupo
 * vuela mientras tanto, la aceptación falla con 409 en vez de pisar la reserva de otro.
 */
@Entity
@Table(name = "reschedule_requests")
@EntityListeners(AuditingEntityListener.class)
public class RescheduleRequest {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "proposed_starts_at", nullable = false, updatable = false)
    private Instant proposedStartsAt;

    @Column(name = "proposed_ends_at", nullable = false, updatable = false)
    private Instant proposedEndsAt;

    @Column(name = "reason", length = 300, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RescheduleStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RescheduleRequest() {
        // exigido por JPA
    }

    public RescheduleRequest(UUID bookingId,
                             UUID requestedBy,
                             Instant proposedStartsAt,
                             Instant proposedEndsAt,
                             String reason) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
        this.proposedStartsAt = Objects.requireNonNull(proposedStartsAt, "proposedStartsAt");
        this.proposedEndsAt = Objects.requireNonNull(proposedEndsAt, "proposedEndsAt");
        this.reason = reason;
        this.status = RescheduleStatus.PENDING;
    }

    public boolean isPending() {
        return status == RescheduleStatus.PENDING;
    }

    public void accept(Instant now) {
        transitionTo(RescheduleStatus.ACCEPTED, now);
    }

    public void decline(Instant now) {
        transitionTo(RescheduleStatus.DECLINED, now);
    }

    /** Llegó la hora de la clase original sin respuesta: la propuesta muere sin efecto. */
    public void expire(Instant now) {
        transitionTo(RescheduleStatus.EXPIRED, now);
    }

    private void transitionTo(RescheduleStatus next, Instant now) {
        if (!isPending()) {
            throw new IllegalStateException("Esta propuesta ya se resolvió (" + status + ")");
        }
        this.status = next;
        this.resolvedAt = Objects.requireNonNull(now, "now");
    }

    /** Quién tiene que responderla: el de la reserva que NO la pidió. */
    public boolean awaitsResponseFrom(UUID userId) {
        return isPending() && !requestedBy.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getProposedStartsAt() {
        return proposedStartsAt;
    }

    public Instant getProposedEndsAt() {
        return proposedEndsAt;
    }

    public String getReason() {
        return reason;
    }

    public RescheduleStatus getStatus() {
        return status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
