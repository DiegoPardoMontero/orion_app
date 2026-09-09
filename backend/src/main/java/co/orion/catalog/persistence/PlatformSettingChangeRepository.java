package co.orion.catalog.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.catalog.domain.PlatformSettingChange;

public interface PlatformSettingChangeRepository extends JpaRepository<PlatformSettingChange, UUID> {

    List<PlatformSettingChange> findTop50ByOrderByChangedAtDesc();

    List<PlatformSettingChange> findByKeyOrderByChangedAtDesc(String key);
}
