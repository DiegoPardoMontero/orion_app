package co.orion.identity.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Para qué está aprendiendo un estudiante. Apunta al MISMO catálogo que los objetivos del
 * profesor: que los dos lados hablen el mismo vocabulario es lo que permite emparejarlos.
 */
@Entity
@Table(name = "student_goals")
@IdClass(StudentGoalId.class)
public class StudentGoal {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "goal_code", length = 30)
    private String goalCode;

    protected StudentGoal() {
        // exigido por JPA
    }

    public StudentGoal(UUID userId, String goalCode) {
        this.userId = userId;
        this.goalCode = goalCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getGoalCode() {
        return goalCode;
    }
}
