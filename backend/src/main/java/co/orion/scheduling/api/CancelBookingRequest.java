package co.orion.scheduling.api;

import jakarta.validation.constraints.Size;

/** El motivo es opcional: cancelar no exige justificarse. */
public record CancelBookingRequest(
        @Size(max = 300, message = "reason no puede superar 300 caracteres")
        String reason) {
}
