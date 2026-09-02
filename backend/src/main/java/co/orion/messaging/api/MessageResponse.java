package co.orion.messaging.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.messaging.domain.Message;

/**
 * Un mensaje como lo ve un participante. {@code body} ya viene enmascarado si la política de
 * contacto actuó; el original nunca sale por aquí (solo la cola de moderación del admin lo ve).
 * {@code mine} le dice al frontend de qué lado pintar la burbuja.
 */
public record MessageResponse(
        UUID id,
        UUID senderId,
        boolean system,
        boolean mine,
        String body,
        String flaggedReason,
        Instant createdAt,
        Instant readAt) {

    public static MessageResponse of(Message message, UUID viewerId) {
        return new MessageResponse(
                message.getId(),
                message.getSenderId(),
                message.isSystem(),
                viewerId.equals(message.getSenderId()),
                message.getBody(),
                message.getFlaggedReason() != null ? message.getFlaggedReason().name() : null,
                message.getCreatedAt(),
                message.getReadAt());
    }
}
