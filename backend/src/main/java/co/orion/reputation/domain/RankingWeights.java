package co.orion.reputation.domain;

/**
 * Los pesos del puntaje, en milésimas (300 = 0,30) para no meter decimales en una tabla de texto.
 * Viven en {@code platform_settings} porque la fórmula es una hipótesis: hay que poder ajustarla
 * cuando haya clases reales que la contradigan, sin desplegar.
 */
public record RankingWeights(int rating,
                             int attendance,
                             int response,
                             int lessons,
                             int completeness,
                             int retention,
                             int sanctionPenaltyPoints) {

    public static RankingWeights defaults() {
        return new RankingWeights(300, 250, 150, 150, 100, 50, 15);
    }
}
