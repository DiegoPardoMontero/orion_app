package co.orion.identity.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;

/**
 * Edición del perfil del profesor. La tarifa NO se toca aquí: va por PUT /me/profile/rate (su
 * respuesta trae el desglose de comisión). La foto tampoco: va por POST /me/photo.
 */
public record UpdateProfileRequest(
        @Size(max = 120) String headline,
        String bio,
        @Size(max = 2) String countryCode,
        @Size(max = 80) String city,
        @Size(max = 5) String nativeLanguage,
        Short yearsExperience,
        @Size(max = 300) String education,
        boolean certified,
        boolean acceptsTrial,
        List<LanguageEntry> languages,
        List<String> goals,
        @JsonProperty("isPublished") boolean isPublished) {

    /** Un idioma que el profesor enseña, con sus niveles. */
    public record LanguageEntry(String code, boolean isNative, List<String> levels) {
    }
}
