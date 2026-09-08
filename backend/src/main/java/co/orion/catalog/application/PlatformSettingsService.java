package co.orion.catalog.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.domain.PlatformSetting;
import co.orion.catalog.domain.PlatformSettingChange;
import co.orion.catalog.domain.SettingDefinition;
import co.orion.catalog.persistence.PlatformSettingChangeRepository;
import co.orion.catalog.persistence.PlatformSettingRepository;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * Lee y escribe los umbrales de negocio (comisión, horas de cancelación…). El valor se guarda como
 * texto y se interpreta aquí según la clave, para que cambiar una regla sea un UPDATE, no un deploy.
 */
@Service
public class PlatformSettingsService {

    private final PlatformSettingRepository settings;
    private final PlatformSettingChangeRepository changes;
    private final Clock clock;

    public PlatformSettingsService(PlatformSettingRepository settings,
                                   PlatformSettingChangeRepository changes,
                                   Clock clock) {
        this.settings = settings;
        this.changes = changes;
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

    /**
     * Cambia un ajuste, validando contra su definición y dejando el cambio en el historial.
     *
     * <p>La validación va aquí y no en el formulario porque el formulario no es la única puerta:
     * el endpoint sigue existiendo y un cero de más en la comisión cambia lo que cobra cada
     * reserva desde ese instante. Un ajuste que no está en el catálogo se rechaza como
     * desconocido — no se acepta a ciegas «por si acaso».
     */
    @Transactional
    public PlatformSetting update(String key, String value, UUID actorId) {
        PlatformSetting setting = get(key);
        SettingDefinition definition = SettingDefinition.forKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Ajuste desconocido: " + key));

        String limpio = definition.validate(value);
        String anterior = setting.getValue();
        if (limpio.equals(anterior)) {
            return setting; // guardar lo mismo no es un cambio y no ensucia el historial
        }

        setting.changeValue(limpio, actorId, clock.instant());
        PlatformSetting guardado = settings.save(setting);
        changes.save(new PlatformSettingChange(key, anterior, limpio, actorId));
        return guardado;
    }

    /** Los últimos cambios, para la pantalla de ajustes. */
    @Transactional(readOnly = true)
    public List<PlatformSettingChange> recentChanges() {
        return changes.findTop50ByOrderByChangedAtDesc();
    }

    private PlatformSetting get(String key) {
        return settings.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Ajuste desconocido: " + key));
    }
}
