package co.orion.reputation.api;

import java.time.Instant;
import java.util.UUID;

/** Una reseña reportada en la cola de moderación del admin: con ambas partes y el motivo del reporte. */
public record ReportedReviewResponse(
        UUID id,
        UUID professorId,
        String professorName,
        UUID studentId,
        String studentName,
        short rating,
        String comment,
        Instant reportedAt,
        String reportedReason,
        Instant createdAt) {
}
