package co.orion.billing.domain;

/** Qué es cada línea de una liquidación. */
public enum PayoutLineKind {

    /** Una clase dictada: su valor, la comisión congelada y el neto del profe. */
    CLASS,

    /**
     * Una clase que el estudiante canceló tarde: no se dictó, pero la política de cancelación le da
     * ese dinero al profe (decisión de Pardo del 25/09/2026).
     */
    LATE_CANCELLATION,

    /** Un reclamo o una devolución a favor del estudiante sobre una clase que ya se le pagó: se descuenta. */
    REFUND_ADJUSTMENT,

    /** El saldo de una liquidación que quedó en cero o en negativo y no se pagó: pasa a la siguiente. */
    CARRY_OVER
}
