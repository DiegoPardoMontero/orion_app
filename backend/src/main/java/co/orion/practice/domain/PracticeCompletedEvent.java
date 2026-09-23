package co.orion.practice.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Un estudiante terminó una práctica. Lo escucha {@code engagement} para dar los puntos —el índice
 * único de su libro hace que completar dos veces no los dé dos veces— y para contar la semana en
 * la racha (decisión de Pardo, 22/09/2026).
 */
public record PracticeCompletedEvent(UUID studentId, UUID setId, int itemCount, int correctCount,
                                     Instant completedAt) {
}
