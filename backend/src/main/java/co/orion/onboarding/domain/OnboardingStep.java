package co.orion.onboarding.domain;

import co.orion.identity.domain.UserRole;

/**
 * Lo que la bienvenida le muestra a cada quien una sola vez. El CHECK de la V51 lista los mismos
 * valores: un recorrido nuevo es una constante aquí y una migración allá.
 */
public enum OnboardingStep {

    /** El video de Sofía, para el profesor recién aprobado. */
    WELCOME_VIDEO(UserRole.PROFESSOR),
    /** El recorrido guiado del profesor: su panel, su disponibilidad, sus clases y el acta. */
    TOUR_PROFESSOR(UserRole.PROFESSOR),
    /** El recorrido guiado del estudiante: buscar, reservar, sus clases y la práctica. */
    TOUR_STUDENT(UserRole.STUDENT);

    private final UserRole rol;

    OnboardingStep(UserRole rol) {
        this.rol = rol;
    }

    /** Cada paso es de un rol: un estudiante no marca como visto el recorrido del profesor. */
    public boolean esDe(UserRole quien) {
        return rol == quien;
    }
}
