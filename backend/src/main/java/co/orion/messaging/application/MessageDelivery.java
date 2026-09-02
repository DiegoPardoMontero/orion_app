package co.orion.messaging.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.messaging.domain.Conversation;
import co.orion.messaging.domain.Message;
import co.orion.messaging.persistence.ConversationRepository;
import co.orion.messaging.persistence.MessageRepository;

/**
 * La parte transaccional del aviso de un mensaje nuevo: crea la notificación in-app para la
 * contraparte y sella el mensaje con {@code notified_at} para que reintentos o dobles eventos no
 * dupliquen el aviso. El correo (efecto externo, puede fallar) lo manda el listener aparte, una vez
 * confirmada esta transacción, para que un fallo de SMTP no revierta la notificación.
 */
@Service
public class MessageDelivery {

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final UserRepository users;
    private final NotificationService notifications;

    public MessageDelivery(MessageRepository messages,
                           ConversationRepository conversations,
                           UserRepository users,
                           NotificationService notifications) {
        this.messages = messages;
        this.conversations = conversations;
        this.users = users;
        this.notifications = notifications;
    }

    /** Lo que el listener necesita para el correo. Vacío si no hay nada que enviar (ya avisado, etc.). */
    public record Delivery(String recipientEmail, String recipientName, String senderName, UUID conversationId) {
    }

    // REQUIRES_NEW: este método corre en el callback AFTER_COMMIT. En un hilo async real no hay
    // transacción y REQUIRED bastaría; pero si el executor fuese síncrono, correría sobre el hilo
    // cuya transacción ya se está cerrando, y REQUIRED se uniría a esa transacción moribunda y
    // perdería la escritura. REQUIRES_NEW garantiza una transacción propia que sí confirma.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Delivery> deliver(UUID messageId, java.time.Instant now) {
        Message message = messages.findById(messageId).orElse(null);
        if (message == null || message.isSystem() || message.getNotifiedAt() != null) {
            return Optional.empty(); // idempotente: ya avisado o nada que avisar
        }

        Conversation conversation = conversations.findById(message.getConversationId()).orElse(null);
        if (conversation == null) {
            return Optional.empty();
        }

        UUID recipientId = conversation.counterpartOf(message.getSenderId());
        User recipient = users.findById(recipientId).orElse(null);
        User sender = users.findById(message.getSenderId()).orElse(null);
        if (recipient == null || sender == null) {
            return Optional.empty();
        }

        String linkPath = "/mensajes/" + conversation.getId();
        notifications.create(recipientId, "MESSAGE",
                "Nuevo mensaje de " + sender.getFullName(),
                previewOf(message.getBody()),
                linkPath);

        message.markNotified(now);
        messages.save(message);

        return Optional.of(new Delivery(
                recipient.getEmail(), recipient.getFullName(), sender.getFullName(), conversation.getId()));
    }

    /** Un adelanto corto del mensaje para el cuerpo de la notificación. */
    private String previewOf(String body) {
        String trimmed = body.strip();
        return trimmed.length() <= 160 ? trimmed : trimmed.substring(0, 157) + "…";
    }
}
