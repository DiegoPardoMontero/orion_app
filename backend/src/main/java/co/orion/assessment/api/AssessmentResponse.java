package co.orion.assessment.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El diagnóstico visto por su dueño.
 *
 * <p><strong>Nunca lleva la transcripción.</strong> Ni aquí ni en la vista del profesor: lo que la
 * persona dijo mientras practicaba no es material de consulta de nadie. Lo que viaja es el número,
 * las cinco dimensiones, las observaciones y los tres nombres.
 */
public record AssessmentResponse(
        UUID id,
        String languageCode,
        int sequence,
        String status,
        String mode,
        Integer score,
        /** El nombre del tramo («Ya te defiendes»). Hay etiqueta aunque no haya número. */
        String label,
        String scoreVersion,
        /** Las cinco dimensiones normalizadas. Vacío mientras no haya terminado. */
        Map<String, Integer> signals,
        String summary,
        List<String> observations,
        Integer durationSeconds,
        Integer turnCount,
        ZonedDateTime startedAt,
        ZonedDateTime completedAt,
        List<RecommendationView> recommendations) {

    public record RecommendationView(UUID professorId, int position, String reasonCode,
                                     String reasonText, boolean booked) {
    }
}
