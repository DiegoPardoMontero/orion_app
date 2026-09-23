package co.orion.teaching.domain;

import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una palabra nueva de la clase, con su significado corto en español. */
@Entity
@Table(name = "lesson_vocabulary")
public class LessonVocabulary {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "lesson_note_id", nullable = false, updatable = false)
    private UUID lessonNoteId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "language_code", length = 5, updatable = false)
    private String languageCode;

    @Column(name = "term", nullable = false, length = 120)
    private String term;

    @Column(name = "meaning", length = 300)
    private String meaning;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected LessonVocabulary() {
    }

    public LessonVocabulary(UUID lessonNoteId, UUID studentId, String languageCode, String term,
                            String meaning, int displayOrder) {
        this.lessonNoteId = lessonNoteId;
        this.studentId = studentId;
        this.languageCode = languageCode;
        this.term = term;
        this.meaning = meaning;
        this.displayOrder = (short) displayOrder;
    }

    public UUID getLessonNoteId() {
        return lessonNoteId;
    }

    public String getTerm() {
        return term;
    }

    public String getMeaning() {
        return meaning;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }
}
