package co.orion.practice.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Un estudiante terminó una práctica. Lo escucha {@code engagement} para dar los puntos —el índice
 * único de su libro hace que completar dos veces no los dé dos veces— y para contar la semana en
 * la racha (decisión de Pardo, 22/09/2026). Desde el 24/09 trae también lo que los logros de la
 * práctica necesitan saber, para que engagement no tenga que leer las tablas de la práctica.
 *
 * @param perfecta           todo lo que se respondió, al primer intento (lo saltado no cuenta)
 * @param escuchaAcertada    ejercicios de escucha acertados
 * @param segundaOportunidad ejercicios acertados al segundo intento
 */
public record PracticeCompletedEvent(UUID studentId, UUID setId, int itemCount, int correctCount,
                                     Instant completedAt, boolean perfecta, int escuchaAcertada,
                                     int segundaOportunidad) {
}
