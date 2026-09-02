package co.orion.billing.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record GeneratePayoutsRequest(@NotNull LocalDate periodStart,
                                     @NotNull LocalDate periodEnd) {
}
