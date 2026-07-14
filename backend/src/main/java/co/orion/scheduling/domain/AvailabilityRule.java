package co.orion.scheduling.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Franja semanal recurrente de un profesor: "los lunes de 18:00 a 21:00".
 *
 * professorId es un UUID plano, no una relación @ManyToOne hacia User: scheduling e identity
 * son módulos distintos y una relación JPA entre ellos los amarraría (cargas perezosas
 * cruzando la frontera, cascadas accidentales, imposibilidad de separarlos después).
 * La integridad referencial la garantiza la FK de la base, que es donde debe vivir.
 */
@Entity
@Table(name = "availability_rules")
@EntityListeners(AuditingEntityListener.class)
public class AvailabilityRule {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Convert(converter = DayOfWeekConverter.class)
    @Column(name = "weekday", nullable = false)
    private DayOfWeek weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AvailabilityRule() {
        // exigido por JPA
    }

    public AvailabilityRule(UUID professorId, DayOfWeek weekday, LocalTime startTime, LocalTime endTime) {
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.weekday = Objects.requireNonNull(weekday, "weekday");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.active = true;
    }

    /** Pausar sin borrar. Todavía ningún endpoint la expone; el calculador sí la respeta. */
    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public DayOfWeek getWeekday() {
        return weekday;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
