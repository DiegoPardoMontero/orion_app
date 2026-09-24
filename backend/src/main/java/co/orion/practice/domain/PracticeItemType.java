package co.orion.practice.domain;

/**
 * Los diez tipos de ejercicio, cada uno anclado a una parte del acta y dentro de una categoría. Los
 * cinco primeros son los del brief (paso B2); los otros cinco llegaron el 24/09/2026 para que la
 * práctica fuera más variada e interactiva.
 */
public enum PracticeItemType {
    /** Completar la frase con el término correcto. Sale del vocabulario. */
    FILL_BLANK(PracticeCategory.PALABRAS),
    /** Corregir una frase con el error recurrente. Sale de «para tener presente». */
    FIX_SENTENCE(PracticeCategory.FRASES),
    /** Emparejar términos con su significado. Sale del vocabulario. */
    MATCH_MEANING(PracticeCategory.PALABRAS),
    /** Ordenar las intervenciones de una conversación corta. Sale de «lo que trabajaron». */
    ORDER_DIALOGUE(PracticeCategory.CONVERSACION),
    /** Escribir una frase propia usando un término. Sale del vocabulario. */
    WRITE_SENTENCE(PracticeCategory.TU_TURNO),
    /** Tocar la palabra que está mal en una frase con el error recurrente. Sale de «para tener presente». */
    SPOT_ERROR(PracticeCategory.FRASES),
    /** Armar una frase con fichas desordenadas, guiado por su sentido en español. Sale de la clase. */
    BUILD_SENTENCE(PracticeCategory.FRASES),
    /** Elegir la mejor respuesta a un mensaje de chat. Sale de «lo que trabajaron». */
    CHOOSE_REPLY(PracticeCategory.CONVERSACION),
    /** Oír un término y elegir qué significa. Sale del vocabulario. */
    LISTEN_CHOOSE(PracticeCategory.ESCUCHA),
    /** Oír una frase corta y escribirla. Sale del vocabulario. */
    DICTATION(PracticeCategory.ESCUCHA);

    private final PracticeCategory categoria;

    PracticeItemType(PracticeCategory categoria) {
        this.categoria = categoria;
    }

    public PracticeCategory categoria() {
        return categoria;
    }

    /** Suena con la voz del dispositivo: si no hay voz en inglés, se puede saltar sin contar como error. */
    public boolean seOye() {
        return categoria == PracticeCategory.ESCUCHA;
    }
}
