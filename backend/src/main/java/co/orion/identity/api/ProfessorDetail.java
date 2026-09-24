package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * Detalle público de un profesor publicado. Muestra hourlyRateCop pero NUNCA la comisión ni el
 * modelo de compensación (eso es interno). ratingAvg es null con menos de 3 reseñas visibles (gate
 * de exhibición); ratingCount es siempre el conteo real de reseñas visibles.
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
        /** Si ofrece clase de prueba: el interruptor encendido y un precio fijado. */
        boolean acceptsTrial,
        /** El precio de su clase de prueba (0 = gratis), o null si no la ofrece. */
        Long trialPriceCop,
        Long hourlyRateCop,
        Double ratingAvg,
        int ratingCount,
        List<ProfileLanguage> languages,
        List<String> goals) {
}
