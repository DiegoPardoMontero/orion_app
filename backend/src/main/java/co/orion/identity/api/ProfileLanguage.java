package co.orion.identity.api;

import java.util.List;

/** Un idioma del profesor con sus niveles, para editar el perfil y para el detalle público. */
public record ProfileLanguage(
        String code,
        String nameEs,
        String nameEn,
        String flagEmoji,
        boolean isNative,
        List<String> levels) {
}
