package co.orion.scheduling.domain;

import java.util.UUID;

/** Una reserva venció sin pago y su cupo quedó libre. No es una cancelación: nadie la canceló. */
public record BookingExpiredEvent(UUID bookingId) {
}
