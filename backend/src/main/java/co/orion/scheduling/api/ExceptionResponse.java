package co.orion.scheduling.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import co.orion.scheduling.domain.AvailabilityException;

/** startTime y endTime nulos = bloqueo de día completo. */
public record ExceptionResponse(UUID id, LocalDate date, LocalTime startTime, LocalTime endTime, String reason) {

    public static ExceptionResponse from(AvailabilityException exception) {
        return new ExceptionResponse(
                exception.getId(),
                exception.getExceptionDate(),
                exception.getStartTime(),
                exception.getEndTime(),
                exception.getReason());
    }
}
