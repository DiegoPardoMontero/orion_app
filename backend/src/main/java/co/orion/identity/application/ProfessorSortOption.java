package co.orion.identity.application;

/**
 * Orden del buscador. RATING cae a RELEVANCE por ahora: el ranking real llega en el Bloque 6, no se
 * inventa aquí. RELEVANCE es un orden estable (certificados primero, luego por id) mientras tanto.
 */
public enum ProfessorSortOption {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    RATING
}
