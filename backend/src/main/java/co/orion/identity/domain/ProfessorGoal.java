package co.orion.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Un objetivo de aprendizaje que cubre un profesor. */
@Entity
@Table(name = "professor_goals")
@IdClass(ProfessorGoalId.class)
public class ProfessorGoal {

    @Id
    @Column(name = "professor_id")
    private UUID professorId;

    @Id
    @Column(name = "goal_code", length = 30)
    private String goalCode;

    protected ProfessorGoal() {
        // exigido por JPA
    }

    public ProfessorGoal(UUID professorId, String goalCode) {
        this.professorId = professorId;
        this.goalCode = goalCode;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public String getGoalCode() {
        return goalCode;
    }
}
