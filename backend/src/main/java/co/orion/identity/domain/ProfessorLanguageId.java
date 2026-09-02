package co.orion.identity.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clave compuesta de {@link ProfessorLanguage}: (profesor, idioma). */
public class ProfessorLanguageId implements Serializable {

    private UUID professorId;
    private String languageCode;

    public ProfessorLanguageId() {
    }

    public ProfessorLanguageId(UUID professorId, String languageCode) {
        this.professorId = professorId;
        this.languageCode = languageCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProfessorLanguageId that)) {
            return false;
        }
        return Objects.equals(professorId, that.professorId) && Objects.equals(languageCode, that.languageCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(professorId, languageCode);
    }
}
