package co.orion.scheduling.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * Bloqueo puntual de un profesor en una fecha concreta. Si startTime y endTime son nulos,
 * bloquea el día completo; si están ambos presentes, bloquea solo ese rango.
 */
@Entity
@Table(name = "availability_exceptions")
@EntityListeners(AuditingEntityListener.class)
public class AvailabilityException {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "reason", length = 200)
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AvailabilityException() {
        // exigido por JPA
    }

    /** Bloqueo de día completo. */
    public static AvailabilityException wholeDay(UUID professorId, LocalDate date, String reason) {
        return new AvailabilityException(professorId, date, null, null, reason);
    }

    /** Bloqueo parcial: [startTime, endTime). */
    public static AvailabilityException partial(UUID professorId, LocalDate date,
                                                LocalTime startTime, LocalTime endTime, String reason) {
        return new AvailabilityException(professorId, date,
                Objects.requireNonNull(startTime, "startTime"),
                Objects.requireNonNull(endTime, "endTime"),
                reason);
    }

    private AvailabilityException(UUID professorId, LocalDate exceptionDate,
                                  LocalTime startTime, LocalTime endTime, String reason) {
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.exceptionDate = Objects.requireNonNull(exceptionDate, "exceptionDate");
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
    }

    public boolean isWholeDay() {
        return startTime == null && endTime == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public LocalDate getExceptionDate() {
        return exceptionDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
