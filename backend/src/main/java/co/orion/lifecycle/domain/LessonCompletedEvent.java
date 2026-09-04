package co.orion.lifecycle.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * La clase se cerró: ocurrió, nadie reclamó y el pago se liberó.
 *
 * <p>Lo publica el cierre automático, que es quien decide en esta arquitectura cuándo una clase
 * está terminada de verdad —no la hora de fin—. Quien escuche esto no forma parte de `lifecycle`:
 * `engagement` lo usa para conceder puntos, y `lifecycle` no sabe qué es un punto.
 */
public record LessonCompletedEvent(UUID bookingId, UUID studentId, Instant completedAt) {
}
