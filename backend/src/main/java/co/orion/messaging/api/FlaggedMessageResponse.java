package co.orion.messaging.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.messaging.domain.Message;

/**
 * Un mensaje marcado, como lo ve el admin en la cola de moderación: aquí SÍ viaja el texto original
 * junto al enmascarado, porque el objetivo es revisar qué se intentó compartir.
 */
public record FlaggedMessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String flaggedReason,
        String body,
        String bodyOriginal,
        Instant createdAt) {

    public static FlaggedMessageResponse of(Message message) {
        return new FlaggedMessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getFlaggedReason() != null ? message.getFlaggedReason().name() : null,
                message.getBody(),
                message.getBodyOriginal(),
                message.getCreatedAt());
    }
}
