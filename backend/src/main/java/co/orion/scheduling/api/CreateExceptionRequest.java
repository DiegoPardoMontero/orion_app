package co.orion.scheduling.api;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * startTime y endTime van juntos o ninguno: ambos nulos significa bloquear el día completo.
 * A diferencia de las reglas, un bloqueo parcial NO exige minutos en :00 (decisión 6 del brief).
 */
public record CreateExceptionRequest(
        @NotNull(message = "date es obligatorio")
        LocalDate date,

        LocalTime startTime,

        LocalTime endTime,

        @Size(max = 200, message = "reason no puede superar 200 caracteres")
        String reason) {
}
