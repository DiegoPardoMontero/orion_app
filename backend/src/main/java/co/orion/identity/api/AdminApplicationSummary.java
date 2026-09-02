package co.orion.identity.api;

import java.time.Instant;
import java.util.UUID;

/** Fila de la bandeja de postulaciones del admin. */
public record AdminApplicationSummary(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        String status,
        Instant submittedAt,
        Instant createdAt) {
}
