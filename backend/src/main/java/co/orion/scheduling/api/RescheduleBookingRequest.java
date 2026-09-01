package co.orion.scheduling.api;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/** El nuevo cupo al que se mueve la clase, con offset explícito como el resto de horarios. */
public record RescheduleBookingRequest(
        @NotNull(message = "startsAt es obligatorio") OffsetDateTime startsAt) {
}
