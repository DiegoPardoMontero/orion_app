package co.orion.identity.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clave compuesta de {@link ProfessorLanguageLevel}: (profesor, idioma, nivel). */
public class ProfessorLanguageLevelId implements Serializable {

    private UUID professorId;
    private String languageCode;
    private String level;

    public ProfessorLanguageLevelId() {
    }

    public ProfessorLanguageLevelId(UUID professorId, String languageCode, String level) {
        this.professorId = professorId;
        this.languageCode = languageCode;
        this.level = level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProfessorLanguageLevelId that)) {
            return false;
        }
        return Objects.equals(professorId, that.professorId)
                && Objects.equals(languageCode, that.languageCode)
                && Objects.equals(level, that.level);
    }

    @Override
    public int hashCode() {
        return Objects.hash(professorId, languageCode, level);
    }
}
