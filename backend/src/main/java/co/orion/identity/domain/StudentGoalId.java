package co.orion.identity.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clave compuesta de {@link StudentGoal}. */
public class StudentGoalId implements Serializable {

    private UUID userId;
    private String goalCode;

    protected StudentGoalId() {
    }

    public StudentGoalId(UUID userId, String goalCode) {
        this.userId = userId;
        this.goalCode = goalCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StudentGoalId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(goalCode, other.goalCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, goalCode);
    }
}
