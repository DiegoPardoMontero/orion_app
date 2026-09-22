package co.orion.assessment.domain;

/**
 * Por qué se recomienda a este profesor, en orden de prioridad.
 *
 * <p>El orden de declaración ES la prioridad: se escoge el primero que aplique. No es arbitrario —
 * va de lo más específico de esta persona a lo más genérico del profesor. Que enseñe justo para lo
 * que tú quieres el idioma dice más que que sea nativo, porque lo primero habla de ti y lo segundo
 * solo de él.
 *
 * <p><strong>Ninguna razón la escribe el modelo.</strong> Son plantillas, y todo lo que se
 * interpola sale del perfil real. Una recomendación generada libremente puede afirmar cosas falsas
 * sobre una persona real, y eso no es un fallo de producto: es una acusación con nuestro membrete.
 */
public enum ReasonCode {

    /** Coincide con lo que la persona dijo que quiere hacer con el idioma. */
    GOAL_MATCH,

    /** Enseña en el nivel que el diagnóstico infirió. */
    LEVEL_MATCH,

    /** Tiene cupos en la franja que la persona suele buscar. */
    SCHEDULE_MATCH,

    /** Es hablante nativo del idioma evaluado. */
    NATIVE,

    /** Tiene una especialidad declarada en su perfil. */
    SPECIALTY,

    /**
     * Entró para completar los tres y no le aplica nada más específico. Dice lo único que es cierto
     * de todo profesor publicado: que pasó la verificación.
     */
    VERIFIED
}
