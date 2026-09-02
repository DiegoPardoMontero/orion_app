package co.orion.messaging.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.identity.domain.User;
import co.orion.messaging.application.ConversationService.ConversationView;
import co.orion.messaging.domain.Conversation;
import co.orion.messaging.domain.Message;

/** Una conversación en la bandeja: con quién, el último mensaje y cuántos van sin leer. */
public record ConversationSummaryResponse(
        UUID id,
        Counterpart counterpart,
        LastMessage lastMessage,
        Instant lastMessageAt,
        int unreadCount) {

    public record Counterpart(UUID id, String fullName, String photoUrl, String role) {
        static Counterpart of(User user) {
            if (user == null) {
                return null;
            }
            return new Counterpart(user.getId(), user.getFullName(), user.getPhotoUrl(),
                    user.getRole().name());
        }
    }

    public record LastMessage(String body, boolean system, boolean mine, Instant createdAt) {
        static LastMessage of(Message message, UUID viewerId) {
            if (message == null) {
                return null;
            }
            return new LastMessage(message.getBody(), message.isSystem(),
                    viewerId.equals(message.getSenderId()), message.getCreatedAt());
        }
    }

    public static ConversationSummaryResponse of(ConversationView view, UUID viewerId) {
        Conversation conversation = view.conversation();
        return new ConversationSummaryResponse(
                conversation.getId(),
                Counterpart.of(view.counterpart()),
                LastMessage.of(view.lastMessage(), viewerId),
                conversation.getLastMessageAt(),
                view.unreadCount());
    }
}
