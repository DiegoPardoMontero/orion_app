package co.orion.assessment.domain;

/**
 * Cómo transcurrió la conversación.
 *
 * <p>{@code FROM_ZERO} es la rama en español: se activa cuando la persona no puede sostener la
 * conversación en el idioma. No produce puntaje, y eso es deliberado — mostrarle un número bajo a
 * alguien que está empezando desde cero es exactamente lo que Orión no hace.
 */
public enum AssessmentMode {
    STANDARD,
    FROM_ZERO
}
