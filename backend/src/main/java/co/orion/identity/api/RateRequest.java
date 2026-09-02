package co.orion.identity.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Los límites replican el CHECK de la base (20.000–500.000 COP): integridad, no política. */
public record RateRequest(
        @NotNull @Min(20000) @Max(500000) Long hourlyRateCop) {
}
