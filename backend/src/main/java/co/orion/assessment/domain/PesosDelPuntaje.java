package co.orion.assessment.domain;

/**
 * Cuánto pesa cada dimensión. Vive en {@code platform_settings} para poder afinar la curva sin
 * desplegar, y por eso mismo la versión del puntaje lleva su huella: ver
 * {@link ConfidenceScoreCalculator#versionDe(PesosDelPuntaje)}.
 */
public record PesosDelPuntaje(int arranque,
                              int continuidad,
                              int extension,
                              int autonomia,
                              int soltura) {

    /** Los del diseño: 25 · 25 · 20 · 20 · 10. */
    public static PesosDelPuntaje porDefecto() {
        return new PesosDelPuntaje(25, 25, 20, 20, 10);
    }

    public int total() {
        return arranque + continuidad + extension + autonomia + soltura;
    }
}
