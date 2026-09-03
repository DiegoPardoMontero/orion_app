package co.orion.lifecycle.domain;

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
 * El reclamo de un estudiante sobre una clase. Existe para que "el profesor no se presentó" tenga
 * un cauce dentro de Orión en vez de resolverse por WhatsApp y a criterio de quien conteste.
 *
 * Mientras está abierto, el dinero no se mueve: ni se libera al profesor ni se devuelve. Lo resuelve
 * una persona, con nota obligatoria — un reclamo cerrado sin explicación es una decisión que nadie
 * puede revisar después.
 */
@Entity
@Table(name = "disputes")
@EntityListeners(AuditingEntityListener.class)
public class Dispute {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, updatable = false, length = 40)
    private DisputeReason reasonCode;

    @Column(name = "description", length = 1000, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private DisputeStatus status;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Dispute() {
        // exigido por JPA
    }

    public Dispute(UUID bookingId, UUID openedBy, DisputeReason reasonCode, String description) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.openedBy = Objects.requireNonNull(openedBy, "openedBy");
        this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
        this.description = description;
        this.status = DisputeStatus.OPEN;
    }

    public boolean isOpen() {
        return status.isOpen();
    }

    /** El admin lo tomó: sirve para que dos personas no resuelvan el mismo reclamo a la vez. */
    public void takeForReview() {
        if (status != DisputeStatus.OPEN) {
            throw new IllegalStateException("Este reclamo ya no está abierto");
        }
        this.status = DisputeStatus.UNDER_REVIEW;
    }

    /**
     * Cierra el reclamo. La nota es obligatoria: la decisión mueve dinero de verdad y alguien tiene
     * que poder entender mañana por qué se tomó.
     */
    public void resolve(DisputeStatus outcome, String note, UUID resolvedBy, Instant now) {
        if (!isOpen()) {
            throw new IllegalStateException("Este reclamo ya se resolvió");
        }
        if (outcome == null || outcome.isOpen()) {
            throw new IllegalArgumentException("La resolución tiene que cerrar el reclamo");
        }
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("La resolución necesita una nota");
        }
        this.status = outcome;
        this.resolutionNote = note.trim();
        this.resolvedBy = Objects.requireNonNull(resolvedBy, "resolvedBy");
        this.resolvedAt = Objects.requireNonNull(now, "now");
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getOpenedBy() {
        return openedBy;
    }

    public DisputeReason getReasonCode() {
        return reasonCode;
    }

    public String getDescription() {
        return description;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
