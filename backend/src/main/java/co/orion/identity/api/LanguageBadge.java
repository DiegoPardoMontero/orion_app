package co.orion.identity.api;

/** Idioma para pintar en una tarjeta: código, nombre (ambos idiomas para i18n), bandera y si es nativo. */
public record LanguageBadge(String code, String nameEs, String nameEn, String flagEmoji, boolean isNative) {
}
