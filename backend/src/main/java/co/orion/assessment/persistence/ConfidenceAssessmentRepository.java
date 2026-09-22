package co.orion.assessment.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.assessment.domain.AssessmentStatus;
import co.orion.assessment.domain.ConfidenceAssessment;

public interface ConfidenceAssessmentRepository extends JpaRepository<ConfidenceAssessment, UUID> {

    /** La viva, si la hay. El índice único parcial de la V34 garantiza que no pueda haber dos. */
    Optional<ConfidenceAssessment> findByUserIdAndLanguageCodeAndStatus(
            UUID userId, String languageCode, AssessmentStatus status);

    List<ConfidenceAssessment> findByUserIdOrderByStartedAtDesc(UUID userId);

    /* Los de un lead mientras no tenga cuenta: en cuanto se reclaman, dejan de ser suyos. */

    Optional<ConfidenceAssessment> findByLeadIdAndLanguageCodeAndStatusAndUserIdIsNull(
            UUID leadId, String languageCode, AssessmentStatus status);

    List<ConfidenceAssessment> findByLeadIdAndUserIdIsNull(UUID leadId);

    List<ConfidenceAssessment> findByLeadIdAndUserIdIsNullOrderByStartedAtDesc(UUID leadId);

    Optional<ConfidenceAssessment> findFirstByLeadIdAndLanguageCodeAndStatusAndUserIdIsNullOrderByCompletedAtDesc(
            UUID leadId, String languageCode, AssessmentStatus status);

    /** La última terminada: de aquí sale la fecha desde la que cuenta el enfriamiento. */
    Optional<ConfidenceAssessment> findFirstByUserIdAndLanguageCodeAndStatusOrderByCompletedAtDesc(
            UUID userId, String languageCode, AssessmentStatus status);

    @Query("select coalesce(max(a.sequence), 0) from ConfidenceAssessment a "
            + "where a.userId = :userId and a.languageCode = :language")
    int lastSequence(@Param("userId") UUID userId, @Param("language") String language);

    /** Las que pasaron el plazo de retención y todavía conservan transcripción. */
    @Query("select a.id from ConfidenceAssessment a where a.startedAt < :limite")
    List<UUID> idsOlderThan(@Param("limite") Instant limite);

    long countByStartedAtGreaterThanEqual(Instant desde);
}
