package co.orion.scheduling.domain;

/**
 * Máquina de estados de una reserva. Desde el Bloque 4 hay dos estados vivos, no uno:
 *
 * <pre>
 * PENDING_PAYMENT ──pasarela aprueba──▶ CONFIRMED ──▶ COMPLETED / NO_SHOW / CANCELLED_*
 *        │
 *        └──rechazado o vencido──▶ EXPIRED
 * </pre>
 *
 * Los dos ocupan el cupo del profesor: si PENDING_PAYMENT no lo ocupara, dos estudiantes llegarían
 * al checkout por el mismo horario y uno pagaría una clase que ya no existe.
 */
public enum BookingStatus {

    PENDING_PAYMENT,
    CONFIRMED,
    EXPIRED,
    CANCELLED_BY_STUDENT,
    CANCELLED_BY_PROFESSOR,
    CANCELLED_BY_ADMIN,
    COMPLETED,
    NO_SHOW;

    public boolean isTerminal() {
        return this != CONFIRMED && this != PENDING_PAYMENT;
    }

    /** Los estados que bloquean el horario del profesor: el mismo par que el índice único parcial. */
    public boolean occupiesSlot() {
        return this == CONFIRMED || this == PENDING_PAYMENT;
    }
}
