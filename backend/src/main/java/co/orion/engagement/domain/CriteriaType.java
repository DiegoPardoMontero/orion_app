package co.orion.engagement.domain;

/**
 * Los criterios que cubren los logros: ocho desde el Bloque 8 y cuatro de la práctica (24/09/2026).
 *
 * <p>Son un enum y no un motor genérico de reglas en JSONB a propósito: un intérprete a medio hacer
 * es algo que nadie sabe depurar cuando falla en producción. Ocho evaluadores tipados cubren el
 * catálogo entero, y un logro nuevo del mismo tipo sigue siendo un INSERT.
 */
public enum CriteriaType {
    /** Clases COMPLETED del estudiante. */
    LESSON_COUNT,
    /** Racha actual en semanas. */
    STREAK_WEEKS,
    /** Profesores distintos con al menos una clase completada. */
    DISTINCT_PROFESSORS,
    /** Idiomas distintos y no nulos en clases completadas. */
    DISTINCT_LANGUAGES,
    /** Clases completadas de la modalidad del parámetro. */
    MODALITY_TAKEN,
    /** ¿Ocurrió al menos una vez el evento del parámetro? */
    EVENT_ONCE,
    /** Campos de la ficha diligenciados: foto, objetivo e idioma principal (0–3). */
    PROFILE_COMPLETE,
    /** Días corridos desde la última cancelación del estudiante. */
    NO_CANCELLATIONS_DAYS,
    /** Prácticas terminadas. */
    PRACTICE_COUNT,
    /** Prácticas terminadas con todo al primer intento. */
    PRACTICE_PERFECT,
    /** Ejercicios de escucha acertados. */
    LISTENING_CORRECT,
    /** Ejercicios acertados al segundo intento: se celebra no rendirse. */
    SECOND_TRY_CORRECT
}
