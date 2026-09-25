package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Perfil propio del profesor (para editar). Incluye el modelo de compensación y el desglose de
 * tarifa — cosas que NUNCA viajan en el detalle público. canPublish avisa si le falta la tarifa.
 *
 * @JsonProperty en isPublished: sin él Jackson serializa "published" pero al leer espera "isPublished".
 */
public record ProfileResponse(
        UUID id,
        String fullName,
        String headline,
        String bio,
        String photoUrl,
        String countryCode,
        String city,
        String nativeLanguage,
        Short yearsExperience,
        String education,
        boolean certified,
        boolean acceptsTrial,
        Long hourlyRateCop,
        String compensationModel,
        List<ProfileLanguage> languages,
        List<String> goals,
        RateBreakdownResponse rate,
        @JsonProperty("isPublished") boolean isPublished,
        boolean canPublish) {
}
