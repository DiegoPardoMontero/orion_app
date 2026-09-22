package co.orion.assessment.domain;

/**
 * El nombre del tramo en el que cae un Confidence Score: lo que la persona lee junto al número.
 *
 * <p><strong>No es un nivel del MCER, y por eso no hay letras.</strong> El handoff de Meissa
 * dibujaba «A2», pero el puntaje mide confianza al hablar, no competencia lingüística, y poner una
 * letra sería afirmar algo que no medimos. De ese dibujo se conservan los nombres, que describen
 * cómo se siente hablar y no un certificado (decisión de Pardo, 22/09/2026).
 *
 * <p>Los tramos van en el código y no en {@code platform_settings}: son redacción de una pantalla,
 * no una regla de negocio que mueva dinero o plazos. Clase pura, como {@code SlotCalculator}.
 */
public final class EtiquetaDelPuntaje {

    public static final String PRIMEROS_PASOS = "Primeros pasos";

    private EtiquetaDelPuntaje() {
    }

    /**
     * @param puntaje el Confidence Score, o {@code null} si la conversación no dio número
     * @param modo    en la rama en español no hay número, pero sí etiqueta
     */
    public static String de(Integer puntaje, AssessmentMode modo) {
        if (modo == AssessmentMode.FROM_ZERO || puntaje == null) {
            return PRIMEROS_PASOS;
        }
        if (puntaje >= 85) {
            return "Casi sin pensarlo";
        }
        if (puntaje >= 65) {
            return "Con soltura";
        }
        if (puntaje >= 45) {
            return "Ya te defiendes";
        }
        if (puntaje >= 25) {
            return "Básico con ganas";
        }
        return PRIMEROS_PASOS;
    }
}
