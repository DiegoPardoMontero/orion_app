package co.orion.scheduling.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordAttendanceRequest(
        @NotNull(message = "present es obligatorio")
        Boolean present,

        @Size(max = 500, message = "notes no puede superar 500 caracteres")
        String notes) {
}
