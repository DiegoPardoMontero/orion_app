package co.orion.assessment.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.assessment.domain.AssessmentRecommendation;
import co.orion.assessment.domain.ConfidenceAssessment;
import co.orion.shared.time.BusinessZone;

/** Arma las respuestas del diagnóstico. Un sitio, para que la transcripción no se escape por otro. */
public final class AssessmentViews {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AssessmentViews() {
    }

    public static AssessmentResponse of(ConfidenceAssessment a,
                                        List<AssessmentRecommendation> recomendaciones) {
        return new AssessmentResponse(
                a.getId(),
                a.getLanguageCode(),
                a.getSequence(),
                a.getStatus().name(),
                a.getMode().name(),
                a.getScore() == null ? null : (int) a.getScore(),
                a.getScoreVersion(),
                leerMapa(a.getSignals()),
                a.getSummary(),
                leerLista(a.getObservations()),
                a.getDurationSeconds(),
                a.getTurnCount() == null ? null : (int) a.getTurnCount(),
                enBogota(a.getStartedAt()),
                enBogota(a.getCompletedAt()),
                recomendaciones.stream()
                        .map(r -> new AssessmentResponse.RecommendationView(
                                r.getProfessorId(), r.getPosition(), r.getReasonCode(),
                                r.getReasonText(), r.getBookedAt() != null))
                        .toList());
    }

    private static ZonedDateTime enBogota(java.time.Instant instante) {
        return instante == null ? null : ZonedDateTime.ofInstant(instante, BusinessZone.BOGOTA);
    }

    private static Map<String, Integer> leerMapa(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<Map<String, Integer>>() { });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static List<String> leerLista(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
