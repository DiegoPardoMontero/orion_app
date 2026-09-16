package co.orion.assessment.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.assessment.domain.AssessmentTurn;

public interface AssessmentTurnRepository extends JpaRepository<AssessmentTurn, UUID> {

    List<AssessmentTurn> findByAssessmentIdOrderByTurnIndexAsc(UUID assessmentId);

    long countByAssessmentId(UUID assessmentId);

    void deleteByAssessmentIdIn(Collection<UUID> assessmentIds);

    /**
     * Borra la transcripción conservando las señales. Es la diferencia entera de la retención: la
     * curva de progreso sobrevive, el texto de lo que la persona dijo no.
     */
    @Modifying
    @Query("update AssessmentTurn t set t.transcript = null where t.assessmentId in :ids")
    int scrubTranscripts(@Param("ids") Collection<UUID> ids);

    @Query("select count(t) from AssessmentTurn t where t.assessmentId in :ids and t.transcript is not null")
    long countWithTranscript(@Param("ids") Collection<UUID> ids);
}
