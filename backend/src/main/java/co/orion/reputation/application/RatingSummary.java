package co.orion.reputation.application;

import java.math.BigDecimal;

/**
 * El rating de un profesor tal como se EXHIBE. Gate de exhibición: con menos de 3 reseñas visibles
 * no se muestra promedio (ratingAvg = null), aunque el conteo sí sea real. Un promedio calculado
 * sobre una o dos opiniones es ruido, no señal — y mostrarlo sería engañar al estudiante.
 */
public record RatingSummary(Double ratingAvg, int ratingCount) {

    /** Umbral mínimo de reseñas visibles para publicar un promedio. */
    public static final int MIN_REVIEWS_FOR_AVERAGE = 3;

    public static final RatingSummary EMPTY = new RatingSummary(null, 0);

    public static RatingSummary of(BigDecimal average, int count) {
        if (count < MIN_REVIEWS_FOR_AVERAGE || average == null) {
            return new RatingSummary(null, count);
        }
        return new RatingSummary(average.doubleValue(), count);
    }
}
