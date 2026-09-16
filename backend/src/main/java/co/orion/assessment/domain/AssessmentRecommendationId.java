package co.orion.assessment.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Clave compuesta de `assessment_recommendations`: una recomendación por profesor y diagnóstico. */
@Embeddable
public class AssessmentRecommendationId implements Serializable {

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private UUID assessmentId;

    @Column(name = "professor_id", nullable = false, updatable = false)
    private UUID professorId;

    protected AssessmentRecommendationId() {
    }

    public AssessmentRecommendationId(UUID assessmentId, UUID professorId) {
        this.assessmentId = assessmentId;
        this.professorId = professorId;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof AssessmentRecommendationId id
                && Objects.equals(assessmentId, id.assessmentId)
                && Objects.equals(professorId, id.professorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assessmentId, professorId);
    }
}
