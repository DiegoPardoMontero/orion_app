package co.orion.messaging.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** El estudiante abre una conversación indicando con qué profesor. */
public record CreateConversationRequest(@NotNull UUID professorId) {
}
