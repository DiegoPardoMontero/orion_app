package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * Tarjeta de profesor en el buscador. hourlyRateCop puede ser null (legado FIXED_FEE). NUNCA lleva
 * rating ni número de estudiantes: eso llega en el Bloque 6; inventarlo sería fraude al estudiante.
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
        List<LanguageBadge> languages,
        List<String> levels,
        List<String> goals) {
}
