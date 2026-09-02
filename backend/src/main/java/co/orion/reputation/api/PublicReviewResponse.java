package co.orion.reputation.api;

import java.time.Instant;
import java.util.UUID;

/** Una reseña en el perfil público del profesor: con el nombre del estudiante, sin datos internos. */
public record PublicReviewResponse(
        UUID id,
        String studentName,
        short rating,
        String comment,
        Instant createdAt) {
}
