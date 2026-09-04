package co.orion.identity.domain;

/**
 * A qué vino la cuenta cuando se creó. No es el rol —el rol dice qué es hoy— sino la puerta por la
 * que entró, que es lo que decide qué ve mientras su postulación espera una decisión.
 */
public enum SignupIntent {
    /** Vino a aprender. Es el caso por defecto y el de toda cuenta anterior a esta distinción. */
    LEARN,
    /** Vino a enseñar: se registró desde «Postúlate para dar clases». */
    TEACH
}
