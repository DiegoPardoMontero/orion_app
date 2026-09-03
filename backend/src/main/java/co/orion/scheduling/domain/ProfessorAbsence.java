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
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una ausencia CONFIRMADA de un profesor: hubo reclamo y se resolvió a favor del estudiante.
 *
 * Vive separada de la sanción a propósito. El hecho es permanente y no caduca; la sanción sí, y
 * puede revocarse. Mezclarlos haría que levantar un castigo borrara también la razón por la que se
 * puso, y con ella el historial que la próxima decisión necesita.
 *
 * UNIQUE sobre booking_id: una clase produce como mucho una ausencia, aunque el reclamo se
 * reabriera.
 */
@Entity
@Table(name = "professor_absences")
@EntityListeners(AuditingEntityListener.class)
public class ProfessorAbsence {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "dispute_id", updatable = false)
    private UUID disputeId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProfessorAbsence() {
        // exigido por JPA
    }

    public ProfessorAbsence(UUID professorId, UUID bookingId, UUID disputeId, Instant occurredAt) {
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId");
        this.disputeId = disputeId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getDisputeId() {
        return disputeId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
