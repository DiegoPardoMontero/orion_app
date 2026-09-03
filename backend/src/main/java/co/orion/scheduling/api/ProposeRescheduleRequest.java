package co.orion.scheduling.api;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** El horario propuesto tiene que ser un cupo real de la agenda del profesor; el servicio lo valida. */
public record ProposeRescheduleRequest(@NotNull OffsetDateTime startsAt,
                                       @Size(max = 300) String reason) {
}
