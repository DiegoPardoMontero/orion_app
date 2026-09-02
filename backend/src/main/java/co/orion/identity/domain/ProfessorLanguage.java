package co.orion.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Un idioma que enseña un profesor. Los niveles van aparte, en {@link ProfessorLanguageLevel}. */
@Entity
@Table(name = "professor_languages")
@IdClass(ProfessorLanguageId.class)
public class ProfessorLanguage {

    @Id
    @Column(name = "professor_id")
    private UUID professorId;

    @Id
    @Column(name = "language_code", length = 5)
    private String languageCode;

    @Column(name = "is_native", nullable = false)
    private boolean isNative;

    protected ProfessorLanguage() {
        // exigido por JPA
    }

    public ProfessorLanguage(UUID professorId, String languageCode, boolean isNative) {
        this.professorId = professorId;
        this.languageCode = languageCode;
        this.isNative = isNative;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public boolean isNative() {
        return isNative;
    }
}
