package co.orion.scheduling.application;

import java.time.Instant;

import co.orion.identity.domain.User;
import co.orion.scheduling.domain.Booking;

/** Una reserva junto a la contraparte que le corresponde ver a quien consulta. */
public record MyBookingsView(Booking booking, User counterpart, Instant now) {
}
