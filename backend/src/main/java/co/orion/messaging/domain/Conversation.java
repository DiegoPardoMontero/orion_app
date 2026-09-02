package co.orion.messaging.domain;

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
 * El hilo entre un estudiante y un profesor. Una sola conversación por par: la constraint UNIQUE
 * (student_id, professor_id) lo garantiza, y el servicio hace get-or-create. Como en el resto del
 * dominio, las referencias a usuarios son UUIDs planos, no relaciones JPA.
 */
@Entity
@Table(name = "conversations")
@EntityListeners(AuditingEntityListener.class)
public class Conversation {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Cuándo llegó el último mensaje. Null hasta el primero; ordena la bandeja. */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    protected Conversation() {
        // exigido por JPA
    }

    public Conversation(UUID studentId, UUID professorId) {
        this.studentId = Objects.requireNonNull(studentId, "studentId");
        this.professorId = Objects.requireNonNull(professorId, "professorId");
    }

    /** ¿Participa este usuario en la conversación? Solo el estudiante y el profesor la ven. */
    public boolean hasParticipant(UUID userId) {
        return studentId.equals(userId) || professorId.equals(userId);
    }

    /** La contraparte de quien mira: si mira el estudiante, el profesor, y viceversa. */
    public UUID counterpartOf(UUID userId) {
        return studentId.equals(userId) ? professorId : studentId;
    }

    public void touch(Instant when) {
        this.lastMessageAt = when;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
