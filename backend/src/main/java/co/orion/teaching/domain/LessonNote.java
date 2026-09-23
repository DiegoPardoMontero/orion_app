package co.orion.teaching.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
 * El acta de una clase: lo que el profesor escribió en crudo y las tres secciones que el estudiante
 * lee (el vocabulario va en su propia tabla).
 *
 * <p>Las reglas que protegen al estudiante viven aquí y en la base (V48): un borrador es invisible
 * para él, un acta publicada no existe sin fecha de publicación, y después de la ventana de
 * edición (72 h por defecto) queda congelada: lo que el estudiante leyó el lunes no puede cambiar
 * en silencio el viernes.
 */
@Entity
@Table(name = "lesson_notes")
public class LessonNote {

    public static final int MAX_SECCION = 1200;
    public static final int MAX_CRUDO = 2000;

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "language_code", length = 5, updatable = false)
    private String languageCode;

    @Column(name = "raw_input", nullable = false, length = MAX_CRUDO)
    private String rawInput;

    @Column(name = "worked_on", length = MAX_SECCION)
    private String workedOn;

    @Column(name = "recurring_issues", length = MAX_SECCION)
    private String recurringIssues;

    @Column(name = "next_steps", length = MAX_SECCION)
    private String nextSteps;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LessonNoteStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private LessonNoteOrigin origin;

    @Column(name = "original_draft")
    private String originalDraft;

    @Column(name = "edit_ratio", precision = 4, scale = 3)
    private BigDecimal editRatio;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LessonNote() {
    }

    public LessonNote(UUID bookingId, UUID professorId, UUID studentId, String languageCode,
                      String rawInput, Instant now) {
        this.bookingId = bookingId;
        this.professorId = professorId;
        this.studentId = studentId;
        this.languageCode = languageCode;
        this.rawInput = rawInput;
        this.status = LessonNoteStatus.DRAFT;
        this.origin = LessonNoteOrigin.MANUAL;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Lo que propuso la IA. Se guarda también como original, para medir después cuánto cambió. */
    public void proponer(String workedOn, String recurringIssues, String nextSteps,
                         String originalComoTexto, String promptVersion, Instant now) {
        exigirBorrador();
        this.workedOn = workedOn;
        this.recurringIssues = recurringIssues;
        this.nextSteps = nextSteps;
        this.origin = LessonNoteOrigin.AI_DRAFT;
        this.originalDraft = originalComoTexto;
        this.promptVersion = promptVersion;
        this.updatedAt = now;
    }

    /** Sin IA: el camino manual, con las notas ya guardadas y los campos vacíos. */
    public void aMano(String rawInput, Instant now) {
        exigirBorrador();
        this.rawInput = rawInput;
        this.origin = LessonNoteOrigin.MANUAL;
        this.originalDraft = null;
        this.promptVersion = null;
        this.updatedAt = now;
    }

    /**
     * Guardar cambios. En borrador, siempre; publicada, solo dentro de la ventana, y queda la fecha
     * de la edición para que el estudiante vea «Actualizada el …».
     */
    public void editar(String workedOn, String recurringIssues, String nextSteps, Duration ventana,
                       Instant now) {
        if (status == LessonNoteStatus.PUBLISHED) {
            if (!editable(ventana, now)) {
                throw new UnprocessableException(
                        "Esta acta ya no se puede editar: pasaron más de " + ventana.toHours()
                                + " horas desde que la publicaste.");
            }
            this.lastEditedAt = now;
        }
        this.workedOn = workedOn;
        this.recurringIssues = recurringIssues;
        this.nextSteps = nextSteps;
        this.updatedAt = now;
    }

    /**
     * Publicar. Idempotente: publicar dos veces no cambia la fecha ni vuelve a avisar.
     *
     * @return si este llamado fue el que la publicó
     */
    public boolean publicar(String textoFinal, Instant now) {
        if (status == LessonNoteStatus.PUBLISHED) {
            return false;
        }
        if (vacia()) {
            throw new UnprocessableException(
                    "El acta está vacía. Escribe al menos una sección antes de publicarla.");
        }
        this.status = LessonNoteStatus.PUBLISHED;
        this.publishedAt = now;
        this.updatedAt = now;
        if (originalDraft != null) {
            this.editRatio = BigDecimal.valueOf(EditRatio.entre(originalDraft, textoFinal));
        }
        return true;
    }

    public boolean editable(Duration ventana, Instant now) {
        return status == LessonNoteStatus.DRAFT
                || (publishedAt != null && now.isBefore(publishedAt.plus(ventana)));
    }

    private boolean vacia() {
        return blank(workedOn) && blank(recurringIssues) && blank(nextSteps);
    }

    private void exigirBorrador() {
        if (status != LessonNoteStatus.DRAFT) {
            throw new UnprocessableException("Esta acta ya está publicada: edítala en vez de regenerarla.");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getProfessorId() { return professorId; }
    public UUID getStudentId() { return studentId; }
    public String getLanguageCode() { return languageCode; }
    public String getRawInput() { return rawInput; }
    public String getWorkedOn() { return workedOn; }
    public String getRecurringIssues() { return recurringIssues; }
    public String getNextSteps() { return nextSteps; }
    public LessonNoteStatus getStatus() { return status; }
    public LessonNoteOrigin getOrigin() { return origin; }
    public BigDecimal getEditRatio() { return editRatio; }
    public String getPromptVersion() { return promptVersion; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getLastEditedAt() { return lastEditedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
