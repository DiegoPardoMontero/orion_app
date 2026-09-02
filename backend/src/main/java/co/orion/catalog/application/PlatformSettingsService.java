package co.orion.catalog.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.domain.PlatformSetting;
import co.orion.catalog.persistence.PlatformSettingRepository;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * Lee y escribe los umbrales de negocio (comisión, horas de cancelación…). El valor se guarda como
 * texto y se interpreta aquí según la clave, para que cambiar una regla sea un UPDATE, no un deploy.
 */
@Service
public class PlatformSettingsService {

    private final PlatformSettingRepository settings;
    private final Clock clock;

    public PlatformSettingsService(PlatformSettingRepository settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public int getInt(String key) {
        return Integer.parseInt(get(key).getValue().trim());
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key).getValue().trim());
    }

    @Transactional(readOnly = true)
    public String getString(String key) {
        return get(key).getValue();
    }

    @Transactional(readOnly = true)
    public List<PlatformSetting> all() {
        return settings.findAll();
    }

    @Transactional
    public PlatformSetting update(String key, String value, UUID actorId) {
        PlatformSetting setting = get(key);
        setting.changeValue(value, actorId, clock.instant());
        return settings.save(setting);
    }

    private PlatformSetting get(String key) {
        return settings.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Ajuste desconocido: " + key));
    }
}
