package co.orion.practice.domain;

public enum PracticeSetStatus {
    /** Creado al publicarse el acta; el trabajo de generación todavía no pasó por él. */
    PENDING,
    READY,
    IN_PROGRESS,
    COMPLETED,
    EXPIRED,
    /** No se pudieron anclar al menos dos ejercicios al acta: no se ofrece nada. */
    FAILED
}
