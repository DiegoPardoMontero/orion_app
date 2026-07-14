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
 * Una clase reservada. Como en el resto de scheduling, las referencias a usuarios son UUIDs
 * planos y no relaciones JPA: la integridad la garantizan las FK, no el grafo de objetos.
 *
 * La columna package_id existe en la tabla (reservada para los paquetes del MVP 2) pero no se
 * mapea aquí: nada la usa todavía, y Hibernate solo valida los atributos que sí declaramos.
 */
@Entity
@Table(name = "bookings")
@EntityListeners(AuditingEntityListener.class)
public class Booking {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality", nullable = false, length = 20)
    private BookingModality modality;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "location_note", length = 300)
    private String locationNote;

    /** Quién ejecutó la acción. Si coincide con student_id, la reserva fue autoservicio. */
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Booking() {
        // exigido por JPA
    }

    public Booking(UUID studentId,
                   UUID professorId,
                   Instant startsAt,
                   Instant endsAt,
                   BookingModality modality,
                   String locationNote,
                   UUID createdBy) {
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
        this.modality = Objects.requireNonNull(modality, "modality");
        this.locationNote = locationNote;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.status = BookingStatus.CONFIRMED;
    }

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
    }

    /** Autoservicio: la reservó el propio estudiante, no un admin en su nombre. */
    public boolean isSelfService() {
        return createdBy.equals(studentId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public BookingModality getModality() {
        return modality;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getLocationNote() {
        return locationNote;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
