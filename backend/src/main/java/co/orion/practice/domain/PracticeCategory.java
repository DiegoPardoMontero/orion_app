package co.orion.practice.domain;

/**
 * Las cinco categorías de la práctica, en el orden en que se recorren: se calienta con palabras y
 * se termina produciendo. Un set trae un ejercicio de cada una cuando el acta alcanza para anclarlas;
 * así nunca son cinco del mismo estilo.
 */
public enum PracticeCategory {
    /** El vocabulario de la clase. */
    PALABRAS,
    /** La gramática y los errores que se repitieron. */
    FRASES,
    /** Lo que se trabajó, dicho entre dos personas. */
    CONVERSACION,
    /** Oír el inglés, con la voz del dispositivo. */
    ESCUCHA,
    /** Producir: una frase propia. */
    TU_TURNO
}
