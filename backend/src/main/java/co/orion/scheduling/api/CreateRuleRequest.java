package co.orion.scheduling.api;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Los tiempos llegan como "18:00" o "18:00:00": ISO_LOCAL_TIME acepta ambos. */
public record CreateRuleRequest(
        @Min(value = 1, message = "weekday debe estar entre 1 (lunes) y 7 (domingo)")
        @Max(value = 7, message = "weekday debe estar entre 1 (lunes) y 7 (domingo)")
        int weekday,

        @NotNull(message = "startTime es obligatorio")
        LocalTime startTime,

        @NotNull(message = "endTime es obligatorio")
        LocalTime endTime) {
}
