package co.orion.practice.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.practice.domain.PracticeItem;

public interface PracticeItemRepository extends JpaRepository<PracticeItem, UUID> {

    List<PracticeItem> findByPracticeSetIdOrderByItemIndexAsc(UUID practiceSetId);

    List<PracticeItem> findByPracticeSetIdIn(Collection<UUID> practiceSetIds);
}
