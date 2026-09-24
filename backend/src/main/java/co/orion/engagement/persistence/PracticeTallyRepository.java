package co.orion.engagement.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.engagement.domain.PracticeTally;

public interface PracticeTallyRepository extends JpaRepository<PracticeTally, UUID> {

    List<PracticeTally> findByUserId(UUID userId);
}
