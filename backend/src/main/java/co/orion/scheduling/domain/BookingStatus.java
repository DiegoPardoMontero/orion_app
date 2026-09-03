package co.orion.scheduling.domain;

/**
 * Máquina de estados de una reserva.
 *
 * <pre>
 * PENDING_PAYMENT ──pago aprobado──▶ CONFIRMED ──┬─ clase dictada ──▶ COMPLETED
 *        │                               │       ├─ no llegó el estudiante ──▶ NO_SHOW_STUDENT
 *        │                               │       ├─ reclamo abierto ──▶ UNDER_REVIEW ──┬─▶ COMPLETED
 *        │                               │       │                                     └─▶ NO_SHOW_PROFESSOR
 *        │                               └─ cancelada ──▶ CANCELLED_BY_*
 *        └──rechazado o vencido──▶ EXPIRED
 * </pre>
 *
 * Los dos no-show son estados distintos y no un matiz: quién faltó decide quién cobra. Si el
 * estudiante no llega, el profesor apartó su hora y estuvo ahí, así que se le paga; si el que falta
 * es el profesor, el estudiante recupera su dinero y queda registrada una ausencia.
 */
public enum BookingStatus {

    PENDING_PAYMENT,
    CONFIRMED,
    UNDER_REVIEW,
    EXPIRED,
    CANCELLED_BY_STUDENT,
    CANCELLED_BY_PROFESSOR,
    CANCELLED_BY_ADMIN,
    COMPLETED,
    NO_SHOW_STUDENT,
    NO_SHOW_PROFESSOR;

    /** Un estado del que ya no se sale. UNDER_REVIEW no lo es: espera a que alguien resuelva. */
    public boolean isTerminal() {
        return this != CONFIRMED && this != PENDING_PAYMENT && this != UNDER_REVIEW;
    }

    /** Los estados que bloquean el horario del profesor: el mismo par que el índice único parcial. */
    public boolean occupiesSlot() {
        return this == CONFIRMED || this == PENDING_PAYMENT;
    }

    /** La clase ocurrió y se cerró: es lo que habilita reseñar y lo que libera el pago. */
    public boolean isClosedAsHeld() {
        return this == COMPLETED || this == NO_SHOW_STUDENT;
    }
}
