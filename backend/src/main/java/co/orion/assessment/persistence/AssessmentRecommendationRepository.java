package co.orion.assessment.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.assessment.domain.AssessmentRecommendation;
import co.orion.assessment.domain.AssessmentRecommendationId;

public interface AssessmentRecommendationRepository
        extends JpaRepository<AssessmentRecommendation, AssessmentRecommendationId> {

    List<AssessmentRecommendation> findByIdAssessmentIdOrderByPositionAsc(UUID assessmentId);
}
