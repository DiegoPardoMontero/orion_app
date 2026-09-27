package co.orion.identity.application;

/**
 * Orden del buscador. RELEVANCE sigue el {@code ranking_score} de reputation y RATING el promedio de
 * reseñas que se exhibe; los dos se resuelven en la consulta (ver ProfessorSpecifications.orderedBy).
 */
public enum ProfessorSortOption {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    RATING
}
