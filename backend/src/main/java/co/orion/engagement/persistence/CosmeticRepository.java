package co.orion.engagement.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.engagement.domain.Cosmetic;
import co.orion.engagement.domain.CosmeticId;
import co.orion.engagement.domain.CosmeticKind;

public interface CosmeticRepository extends JpaRepository<Cosmetic, CosmeticId> {

    List<Cosmetic> findByKindOrderByDisplayOrderAsc(CosmeticKind kind);

    List<Cosmetic> findAllByOrderByKindAscDisplayOrderAsc();
}
