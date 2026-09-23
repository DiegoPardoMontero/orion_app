package co.orion.practice.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import co.orion.shared.error.UnprocessableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un set de práctica: los ejercicios que salen de un acta publicada (brief del Bloque 10, Parte B).
 *
 * <p>Un acta, un set, para siempre (la base lo garantiza con {@code lesson_note_id UNIQUE}), y el
 * set vence a los {@code practice_set_ttl_days}: el viejo no compite con el de la clase siguiente.
 */
@Entity
@Table(name = "practice_sets")
public class PracticeSet {

    @Id
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "lesson_note_id", nullable = false, updatable = false)
    private UUID lessonNoteId;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "language_code", length = 5, updatable = false)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PracticeSetStatus status = PracticeSetStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String material;

    @Column(name = "estimated_minutes")
    private Short estimatedMinutes;

    @Column(name = "item_count", nullable = false)
    private short itemCount;

    @Column(name = "correct_count", nullable = false)
    private short correctCount;

    @Column(name = "generation_attempts", nullable = false)
    private short generationAttempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PracticeSet() {
    }

    public PracticeSet(UUID studentId, UUID lessonNoteId, UUID professorId, String languageCode,
                       String material, Instant expiresAt) {
        this.studentId = studentId;
        this.lessonNoteId = lessonNoteId;
        this.professorId = professorId;
        this.languageCode = languageCode;
        this.material = material;
        this.expiresAt = expiresAt;
    }

    /** El generador dejó los ejercicios: el set se ofrece. */
    public void listo(int items, int minutos) {
        this.status = PracticeSetStatus.READY;
        this.itemCount = (short) items;
        this.estimatedMinutes = (short) minutos;
    }

    /** Menos de dos ejercicios anclados: mejor ninguna práctica que práctica inventada. */
    public void fallido() {
        this.status = PracticeSetStatus.FAILED;
    }

    public void intentoDeGeneracion() {
        this.generationAttempts++;
    }

    /** Si todavía se puede practicar: listo o empezado, y sin vencer. */
    public boolean vivo(Instant ahora) {
        return (status == PracticeSetStatus.READY || status == PracticeSetStatus.IN_PROGRESS)
                && ahora.isBefore(expiresAt);
    }

    public void exigirVivo(Instant ahora) {
        if (!vivo(ahora)) {
            throw new UnprocessableException(status == PracticeSetStatus.COMPLETED
                    ? "Esta práctica ya la terminaste."
                    : "Esta práctica ya venció. La próxima llega con tu siguiente clase.");
        }
    }

    public void empezar(Instant ahora) {
        exigirVivo(ahora);
        if (status == PracticeSetStatus.READY) {
            this.status = PracticeSetStatus.IN_PROGRESS;
            this.startedAt = ahora;
        }
    }

    /** @return {@code true} solo la primera vez: completar dos veces no emite nada más. */
    public boolean completar(int correctos, Instant ahora) {
        if (status == PracticeSetStatus.COMPLETED) {
            return false;
        }
        exigirVivo(ahora);
        this.status = PracticeSetStatus.COMPLETED;
        this.correctCount = (short) correctos;
        this.completedAt = ahora;
        if (startedAt == null) {
            this.startedAt = ahora;
        }
        return true;
    }

    public boolean expirar(Instant ahora) {
        if ((status == PracticeSetStatus.READY || status == PracticeSetStatus.IN_PROGRESS)
                && !ahora.isBefore(expiresAt)) {
            this.status = PracticeSetStatus.EXPIRED;
            return true;
        }
        return false;
    }

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public UUID getLessonNoteId() { return lessonNoteId; }
    public UUID getProfessorId() { return professorId; }
    public String getLanguageCode() { return languageCode; }
    public PracticeSetStatus getStatus() { return status; }
    public String getMaterial() { return material; }
    public Short getEstimatedMinutes() { return estimatedMinutes; }
    public int getItemCount() { return itemCount; }
    public int getCorrectCount() { return correctCount; }
    public int getGenerationAttempts() { return generationAttempts; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
