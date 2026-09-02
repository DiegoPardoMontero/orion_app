package co.orion.reputation.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.reputation.domain.ProfessorMetrics;
import co.orion.reputation.persistence.ProfessorMetricsRepository;

/**
 * La cara de LECTURA de la reputación para el resto de la plataforma (buscador y detalle público).
 * Lee del agregado materializado professor_metrics y aplica el gate de exhibición. Un profesor sin
 * fila de métricas todavía —nunca reseñado— rinde {@link RatingSummary#EMPTY}.
 */
@Service
public class ProfessorRatingService {

    private final ProfessorMetricsRepository metrics;

    public ProfessorRatingService(ProfessorMetricsRepository metrics) {
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public RatingSummary summaryFor(UUID professorId) {
        return metrics.findById(professorId)
                .map(ProfessorRatingService::toSummary)
                .orElse(RatingSummary.EMPTY);
    }

    /** Batch: una consulta para toda una página de profesores; ausentes → EMPTY al leer del mapa. */
    @Transactional(readOnly = true)
    public Map<UUID, RatingSummary> summariesFor(Collection<UUID> professorIds) {
        if (professorIds.isEmpty()) {
            return Map.of();
        }
        return metrics.findByProfessorIdIn(professorIds).stream()
                .collect(Collectors.toMap(ProfessorMetrics::getProfessorId,
                        ProfessorRatingService::toSummary, (a, b) -> a));
    }

    public RatingSummary summaryFrom(Map<UUID, RatingSummary> batch, UUID professorId) {
        return batch.getOrDefault(professorId, RatingSummary.EMPTY);
    }

    private static RatingSummary toSummary(ProfessorMetrics m) {
        return RatingSummary.of(m.getRatingAvg(), m.getRatingCount());
    }
}
