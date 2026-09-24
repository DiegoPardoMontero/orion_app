package co.orion.reputation.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Un estudiante calificó su clase. Lo escuchan los puntos del estudiante y el aviso al profesor;
 * {@code reputation} no sabe de ninguno de los dos.
 */
public record ReviewCreatedEvent(UUID reviewId, UUID bookingId, UUID studentId, UUID professorId,
                                 short rating, Instant createdAt) {
}
