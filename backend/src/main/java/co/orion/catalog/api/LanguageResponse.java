package co.orion.catalog.api;

import co.orion.catalog.domain.Language;

/** Ambos nombres viajan (es/en) para dejar la UI lista para i18n sin un segundo endpoint. */
public record LanguageResponse(String code, String nameEs, String nameEn, String flagEmoji) {

    public static LanguageResponse from(Language language) {
        return new LanguageResponse(
                language.getCode(),
                language.getNameEs(),
                language.getNameEn(),
                language.getFlagEmoji());
    }
}
