package co.orion.scheduling.domain;

import java.util.UUID;

/**
 * Se publica al cancelar.
 *
 * <p>Lleva el estado en que estaba la reserva antes de cancelarse porque después ya no se puede
 * saber: una reserva sin pagar que se suelta y una clase confirmada que se cae terminan las dos en
 * CANCELLED_BY_*. Y no se avisan igual: al profesor nunca se le anunció una reserva sin pagar, así
 * que tampoco se le avisa de que se soltó.
 */
public record BookingCancelledEvent(UUID bookingId, BookingStatus previousStatus) {

    /** Era una reserva esperando el pago: para el profesor, nunca existió. */
    public boolean wasAwaitingPayment() {
        return previousStatus == BookingStatus.PENDING_PAYMENT;
    }
}
