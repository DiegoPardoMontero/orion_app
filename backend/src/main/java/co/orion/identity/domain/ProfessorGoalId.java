package co.orion.identity.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clave compuesta de {@link ProfessorGoal}: (profesor, objetivo). */
public class ProfessorGoalId implements Serializable {

    private UUID professorId;
    private String goalCode;

    public ProfessorGoalId() {
    }

    public ProfessorGoalId(UUID professorId, String goalCode) {
        this.professorId = professorId;
        this.goalCode = goalCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProfessorGoalId that)) {
            return false;
        }
        return Objects.equals(professorId, that.professorId) && Objects.equals(goalCode, that.goalCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(professorId, goalCode);
    }
}
