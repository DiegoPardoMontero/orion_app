package co.orion.scheduling.domain;

import java.util.UUID;

/** Se publica al cancelar. El listener de correos lo escuchará en el Paso 5. */
public record BookingCancelledEvent(UUID bookingId) {
}
