package co.orion.practice.domain;

/** Los cinco tipos del brief (paso B2), cada uno anclado a una parte del acta. */
public enum PracticeItemType {
    /** Completar la frase con el término correcto. Sale del vocabulario. */
    FILL_BLANK,
    /** Corregir una frase con el error recurrente. Sale de «para tener presente». */
    FIX_SENTENCE,
    /** Emparejar términos con su significado. Sale del vocabulario. */
    MATCH_MEANING,
    /** Ordenar las intervenciones de una conversación corta. Sale de «lo que trabajaron». */
    ORDER_DIALOGUE,
    /** Escribir una frase propia usando un término. Sale del vocabulario. */
    WRITE_SENTENCE
}
