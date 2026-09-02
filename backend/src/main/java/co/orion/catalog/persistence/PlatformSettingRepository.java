package co.orion.catalog.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.catalog.domain.PlatformSetting;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {
}
