package co.orion.assessment.domain;

/** En qué punto está una evaluación. Los cuatro valores del CHECK de la V34. */
public enum AssessmentStatus {

    IN_PROGRESS,

    /** Terminó y tiene puntaje. La base garantiza que no exista completada sin puntaje ni fecha. */
    COMPLETED,

    /** Se cortó sin resultado: colgó antes, o no dio turnos suficientes para sostener un número. */
    ABANDONED,

    /** Se rompió algo nuestro o del proveedor. Se distingue de ABANDONED a propósito: una es
     *  decisión de la persona y la otra es culpa nuestra, y no se miden igual. */
    FAILED
}
