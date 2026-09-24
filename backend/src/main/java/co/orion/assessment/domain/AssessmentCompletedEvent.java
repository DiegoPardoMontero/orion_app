package co.orion.assessment.domain;

import java.util.UUID;

/**
 * Una cuenta tiene ya un diagnóstico terminado: lo hizo con su sesión o reclamó el que hizo como
 * lead. Lo escucha {@code engagement} para los puntos del diagnóstico; {@code assessment} no sabe
 * qué es un punto.
 */
public record AssessmentCompletedEvent(UUID userId) {
}
