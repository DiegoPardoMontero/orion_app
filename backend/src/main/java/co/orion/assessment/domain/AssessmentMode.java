package co.orion.assessment.domain;

/**
 * Cómo transcurrió la conversación.
 *
 * <p>{@code FROM_ZERO} es la rama en español: se activa cuando la persona no puede sostener la
 * conversación en el idioma. Hasta el 25/09/2026 no producía puntaje; desde entonces lo produce,
 * contado solo por lo que se dijo en inglés, y recomienda profesores para empezar (Pardo).
 */
public enum AssessmentMode {
    STANDARD,
    FROM_ZERO
}
