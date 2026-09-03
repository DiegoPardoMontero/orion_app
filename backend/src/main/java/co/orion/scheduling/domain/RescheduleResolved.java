package co.orion.scheduling.domain;

import java.util.UUID;

/** La contraparte respondió una propuesta. {@code accepted} distingue mover de dejar como estaba. */
public record RescheduleResolved(UUID requestId, boolean accepted) {
}
