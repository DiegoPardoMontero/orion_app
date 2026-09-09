package co.orion.catalog.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.catalog.domain.PlatformSettingChange;

/** Una línea del historial: de qué a qué, quién y cuándo. */
public record SettingChangeResponse(String key, String label, String oldValue, String newValue,
                                    UUID changedBy, String changedByName, Instant changedAt) {

    public static SettingChangeResponse from(PlatformSettingChange change, String label,
                                             String actorName) {
        return new SettingChangeResponse(change.getKey(), label, change.getOldValue(),
                change.getNewValue(), change.getChangedBy(), actorName, change.getChangedAt());
    }
}
