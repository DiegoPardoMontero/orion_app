package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * Detalle público de un profesor publicado. Muestra hourlyRateCop pero NUNCA la comisión ni el
 * modelo de compensación (eso es interno). Sin rating ni contadores: llegan en el Bloque 6.
 */
public record ProfessorDetail(
        UUID id,
        String fullName,
        String photoUrl,
        String headline,
        String bio,
        String city,
        String countryCode,
        Short yearsExperience,
        String education,
        boolean certified,
        boolean acceptsTrial,
        Long hourlyRateCop,
        List<ProfileLanguage> languages,
        List<String> goals) {
}
