package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * Tarjeta de profesor en el buscador. hourlyRateCop puede ser null (legado FIXED_FEE). ratingAvg es
 * null cuando hay menos de 3 reseñas visibles (gate de exhibición): un promedio sobre una o dos
 * opiniones es ruido, no señal. ratingCount siempre es el conteo real de reseñas visibles.
 */
public record ProfessorCard(
        UUID id,
        String fullName,
        String photoUrl,
        String headline,
        String city,
        String countryCode,
        boolean certified,
        Long hourlyRateCop,
        Double ratingAvg,
        int ratingCount,
        List<LanguageBadge> languages,
        List<String> levels,
        List<String> goals) {
}
