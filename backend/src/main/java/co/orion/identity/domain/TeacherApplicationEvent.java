package co.orion.identity.domain;

import java.time.Instant;
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

/** Una entrada en la bitácora de una postulación. Cada transición escribe exactamente una. */
@Entity
@Table(name = "teacher_application_events")
@EntityListeners(AuditingEntityListener.class)
public class TeacherApplicationEvent {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ApplicationEventType eventType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TeacherApplicationEvent() {
        // exigido por JPA
    }

    public TeacherApplicationEvent(UUID applicationId, ApplicationEventType eventType, UUID actorId, String note) {
        this.applicationId = applicationId;
        this.eventType = eventType;
        this.actorId = actorId;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public ApplicationEventType getEventType() {
        return eventType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
