package co.orion.identity.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;

/**
 * Edición del perfil del profesor. La tarifa NO se toca aquí: va por PUT /me/profile/rate (su
 * respuesta trae el desglose de comisión). La foto tampoco: va por POST /me/photo.
 *
 * <p>Los tres booleanos son {@code Boolean} y no {@code boolean} a propósito: hace falta distinguir
 * "lo desmarcó" de "no lo mandó". Con el primitivo, un cuerpo al que le falte el campo llega como
 * {@code false} y es indistinguible de una casilla desmarcada — que es exactamente cómo el wizard
 * de postulación borraba lo que el aspirante ya había guardado. Quien manda el formulario entero
 * (el editor del profesor aprobado) sigue tratando el nulo como falso, y no cambia nada para él.
 */
public record UpdateProfileRequest(
        @Size(max = 120) String headline,
        @Size(max = 5000) String bio,
        @Size(max = 2) String countryCode,
        @Size(max = 80) String city,
        @Size(max = 5) String nativeLanguage,
        Short yearsExperience,
        @Size(max = 300) String education,
        Boolean certified,
        Boolean acceptsTrial,
        List<LanguageEntry> languages,
        List<String> goals,
        @JsonProperty("isPublished") Boolean isPublished) {

    /** Un idioma que el profesor enseña, con sus niveles. */
    public record LanguageEntry(String code, boolean isNative, List<String> levels) {
    }
}
