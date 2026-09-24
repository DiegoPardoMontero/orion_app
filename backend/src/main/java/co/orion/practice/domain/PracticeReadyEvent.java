package co.orion.practice.domain;

import java.util.UUID;

/** Una práctica quedó lista para hacerse: ya tiene sus ejercicios. */
public record PracticeReadyEvent(UUID setId, UUID studentId, UUID professorId, int itemCount) {
}
