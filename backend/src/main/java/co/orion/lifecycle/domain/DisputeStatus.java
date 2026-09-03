package co.orion.lifecycle.domain;

/**
 * Un reclamo abierto congela la plata: ni se le libera al profesor ni se le devuelve al estudiante
 * hasta que una persona decida. Por eso OPEN y UNDER_REVIEW bloquean el autocompletado.
 */
public enum DisputeStatus {

    OPEN,
    UNDER_REVIEW,
    /** La clase no ocurrió por culpa del profesor: el estudiante recupera su plata. */
    RESOLVED_FOR_STUDENT,
    /** La clase sí ocurrió: el profesor cobra. */
    RESOLVED_FOR_PROFESSOR,
    /** El reclamo no procede (fuera de plazo, sin sustento). Igual que a favor del profesor. */
    DISMISSED;

    public boolean isOpen() {
        return this == OPEN || this == UNDER_REVIEW;
    }

    /** ¿Se resolvió dándole la razón al estudiante? Es lo que crea la ausencia y devuelve el dinero. */
    public boolean favoursStudent() {
        return this == RESOLVED_FOR_STUDENT;
    }
}
