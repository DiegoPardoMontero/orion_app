package co.orion.catalog.api;

import java.time.Instant;

import co.orion.catalog.domain.PlatformSetting;

public record SettingResponse(String key, String value, Instant updatedAt) {

    public static SettingResponse from(PlatformSetting setting) {
        return new SettingResponse(setting.getKey(), setting.getValue(), setting.getUpdatedAt());
    }
}
