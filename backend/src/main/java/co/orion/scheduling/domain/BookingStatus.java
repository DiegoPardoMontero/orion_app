package co.orion.scheduling.domain;

/**
 * Máquina de estados de una reserva: solo CONFIRMED admite transiciones, el resto son terminales.
 */
public enum BookingStatus {

    CONFIRMED,
    CANCELLED_BY_STUDENT,
    CANCELLED_BY_PROFESSOR,
    CANCELLED_BY_ADMIN,
    COMPLETED,
    NO_SHOW;

    public boolean isTerminal() {
        return this != CONFIRMED;
    }
}
