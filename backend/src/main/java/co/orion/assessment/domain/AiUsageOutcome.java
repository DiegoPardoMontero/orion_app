package co.orion.assessment.domain;

/** Cómo terminó una llamada a la IA. Se registra siempre, salga bien o mal. */
public enum AiUsageOutcome {
    OK,
    /** El modelo se negó a responder. */
    REFUSED,
    /** Respondió, pero con algo que no se puede usar. */
    INVALID_OUTPUT,
    ERROR,
    TIMEOUT
}
