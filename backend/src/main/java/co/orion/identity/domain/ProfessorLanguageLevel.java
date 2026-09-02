package co.orion.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Un nivel al que un profesor enseña un idioma concreto. FK compuesta a professor_languages. */
@Entity
@Table(name = "professor_language_levels")
@IdClass(ProfessorLanguageLevelId.class)
public class ProfessorLanguageLevel {

    @Id
    @Column(name = "professor_id")
    private UUID professorId;

    @Id
    @Column(name = "language_code", length = 5)
    private String languageCode;

    @Id
    @Column(name = "level", length = 20)
    private String level;

    protected ProfessorLanguageLevel() {
        // exigido por JPA
    }

    public ProfessorLanguageLevel(UUID professorId, String languageCode, String level) {
        this.professorId = professorId;
        this.languageCode = languageCode;
        this.level = level;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getLevel() {
        return level;
    }
}
