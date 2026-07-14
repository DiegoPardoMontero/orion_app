package co.orion.scheduling.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * startsAt llega con offset explícito, exactamente como lo devuelve el endpoint de cupos
 * (p. ej. "2026-07-20T18:00:00-05:00"), y debe coincidir con un cupo disponible por instante.
 *
 * studentId solo lo puede usar un ADMIN para reservar en nombre de un estudiante.
 */
public record CreateBookingRequest(
        @NotNull(message = "professorId es obligatorio")
        UUID professorId,

        @NotNull(message = "startsAt es obligatorio")
        OffsetDateTime startsAt,

        @NotBlank(message = "modality es obligatoria")
        String modality,

        @Size(max = 300, message = "locationNote no puede superar 300 caracteres")
        String locationNote,

        UUID studentId) {
}
