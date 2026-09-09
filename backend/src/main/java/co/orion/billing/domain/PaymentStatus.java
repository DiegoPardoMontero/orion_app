package co.orion.billing.domain;

/**
 * Ciclo de vida del dinero de una clase:
 *
 * <pre>
 * PENDING ──pasarela aprueba──▶ PAID ──clase dictada──▶ RELEASED ──liquidación──▶ (en un payout)
 *    │                            │
 *    │                            └──cancelada por el profesor──▶ REFUNDED (crédito al estudiante)
 *    └──rechazada o vencida──▶ CANCELLED
 * </pre>
 *
 * Solo un pago RELEASED entra en una liquidación: hasta que la clase ocurre, la plata está retenida.
 */
public enum PaymentStatus {

    PENDING,
    PAID,
    RELEASED,

    /**
     * Se le debe al estudiante y todavía no se le ha transferido.
     *
     * <p>Estado propio y no {@link #DISPUTED} a propósito: un pago en retracto no está en disputa
     * —no hay nada que decidir, la ley ya decidió— sino esperando una transferencia que Wompi no
     * deja hacer por API. Mezclarlos habría puesto en «requiere decisión» algo que solo requiere
     * ejecutarse, y habría escondido lo único que hay que vigilar aquí: cuántos días quedan.
     *
     * <p>De aquí NO se sale hacia {@link #RELEASED}: este dinero nunca es del profesor.
     */
    REFUND_PENDING,

    REFUNDED,
    DISPUTED,
    CANCELLED;

    /** Un pago que ya no espera nada de la pasarela. */
    public boolean isSettled() {
        return this != PENDING;
    }

    /** El dinero está congelado: ni del profesor ni devuelto todavía. */
    public boolean isFrozen() {
        return this == DISPUTED || this == REFUND_PENDING;
    }
}
