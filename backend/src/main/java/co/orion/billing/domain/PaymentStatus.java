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
    REFUNDED,
    DISPUTED,
    CANCELLED;

    /** Un pago que ya no espera nada de la pasarela. */
    public boolean isSettled() {
        return this != PENDING;
    }
}
