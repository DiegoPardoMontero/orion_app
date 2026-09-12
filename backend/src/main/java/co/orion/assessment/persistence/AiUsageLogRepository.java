package co.orion.assessment.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.assessment.domain.AiUsageLog;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, UUID> {

    /**
     * Lo gastado por una función en una ventana. {@code coalesce} porque un día sin gasto tiene que
     * devolver cero y no nulo: si devuelve nulo, la comparación con el tope se salta sola.
     */
    @Query("""
            select coalesce(sum(l.costCop), 0) from AiUsageLog l
            where l.feature = :feature
              and l.occurredAt >= :from
              and l.occurredAt < :to
            """)
    long spentBetween(@Param("feature") String feature,
                      @Param("from") Instant from,
                      @Param("to") Instant to);
}
