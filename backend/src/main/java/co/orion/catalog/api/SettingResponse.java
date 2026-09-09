package co.orion.catalog.api;

import java.time.Instant;
import java.util.List;

import co.orion.catalog.domain.PlatformSetting;
import co.orion.catalog.domain.SettingDefinition;

/**
 * Un ajuste con todo lo que hace falta para dibujarlo y para entenderlo: su grupo, su explicación
 * en español, su tipo y su rango. La pantalla no debería tener que saberse el catálogo de memoria,
 * y la explicación tiene que vivir junto a la validación para que no se separen.
 */
public record SettingResponse(String key, String value, Instant updatedAt,
                              String group, String label, String description,
                              String type, Integer min, Integer max, List<String> options,
                              boolean sensitive) {

    public static SettingResponse from(PlatformSetting setting) {
        return SettingDefinition.forKey(setting.getKey())
                .map(d -> new SettingResponse(setting.getKey(), setting.getValue(),
                        setting.getUpdatedAt(), d.getGrupo().name(), d.getEtiqueta(),
                        d.getExplicacion(), d.getTipo().name(), d.getMin(), d.getMax(),
                        d.getOpciones(), d.isSensitive()))
                // Un ajuste en la base que nadie catalogó: se muestra en crudo en vez de
                // esconderlo. Ocultarlo dejaría un valor vivo que nadie sabe que existe.
                .orElseGet(() -> new SettingResponse(setting.getKey(), setting.getValue(),
                        setting.getUpdatedAt(), "OTROS", setting.getKey(),
                        "Sin catalogar: no se valida ni se explica.", "TEXTO", null, null,
                        List.of(), true));
    }
}
