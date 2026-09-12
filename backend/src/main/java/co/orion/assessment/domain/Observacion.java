package co.orion.assessment.domain;

/**
 * Lo que se notó, como código. El texto lo pone una plantilla y el ejemplo lo aporta el modelo
 * desde la transcripción: así el diagnóstico no puede afirmar nada que una señal no respalde.
 *
 * <p>El orden de la enumeración es el orden en que se evalúan y se muestran. No es alfabético a
 * propósito: <strong>las que reconocen algo van primero</strong>. Quien llega a Orión llega con
 * vergüenza de hablar, y una lista que abre con tres carencias se lee como una nota.
 */
public enum Observacion {

    /** Arrancó rápido y sostenido. */
    STEADY_START,
    /** Se alargó donde el tema era cómodo: ahí tiene más idioma del que usa. */
    LONG_ANSWERS_WHEN_COMFORTABLE,
    /** No se encogió cuando la pregunta pidió elaborar. */
    HOLDS_UNDER_PRESSURE,

    /** Se toma unos segundos antes de empezar. */
    SLOW_START,
    /** Empieza frases y las suelta a mitad. */
    ABANDONS_CLAUSES,
    /** Responde corto aunque entienda. */
    SHORT_ANSWERS,
    /** Se devuelve a su idioma cuando el terreno se pone difícil. */
    RETREATS_TO_NATIVE,
    /** Mucha muletilla y mucha autocorrección por cada cien palabras. */
    FILLER_HEAVY
}
