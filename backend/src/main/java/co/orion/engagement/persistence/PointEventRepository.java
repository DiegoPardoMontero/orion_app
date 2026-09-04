package co.orion.engagement.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.engagement.domain.PointEvent;

public interface PointEventRepository extends JpaRepository<PointEvent, UUID> {

    boolean existsBySourceTypeAndSourceId(String sourceType, UUID sourceId);

    /** El total del estudiante. Se suma del libro; no hay contador que pueda desincronizarse. */
    @Query("select coalesce(sum(e.points), 0) from PointEvent e where e.userId = :userId")
    long totalPointsOf(@Param("userId") UUID userId);

    List<PointEvent> findByUserIdOrderByOccurredAtDesc(UUID userId);

    void deleteByUserId(UUID userId);
}
