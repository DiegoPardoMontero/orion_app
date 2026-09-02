package co.orion.messaging.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.application.ProfessorAccessService;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.messaging.application.ContactMasker.MaskResult;
import co.orion.messaging.domain.Conversation;
import co.orion.messaging.domain.Message;
import co.orion.messaging.persistence.ConversationRepository;
import co.orion.messaging.persistence.MessageRepository;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * El corazón del Bloque 3: el estudiante y el profesor conversan dentro de Orión, con historial
 * auditable. Reglas: solo los dos participantes ven y escriben en un hilo (403 a terceros); el
 * estudiante inicia el hilo con un profesor aprobado (mismo gate que la reserva); cada mensaje pasa
 * por la política de contacto antes de guardarse.
 */
@Service
public class ConversationService {

    private static final String CONTACT_POLICY_KEY = "contact_policy_mode";
    private static final String MASK_MODE = "MASK";

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final UserRepository users;
    private final ProfessorAccessService professorAccess;
    private final PlatformSettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations,
                               MessageRepository messages,
                               UserRepository users,
                               ProfessorAccessService professorAccess,
                               PlatformSettingsService settings,
                               ApplicationEventPublisher events,
                               Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.professorAccess = professorAccess;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    /** La contraparte, el último mensaje y los no leídos de una conversación, listos para la bandeja. */
    public record ConversationView(Conversation conversation, User counterpart,
                                   Message lastMessage, int unreadCount) {
    }

    /**
     * El estudiante abre (o reencuentra) su conversación con un profesor. get-or-create: la
     * constraint UNIQUE (student_id, professor_id) garantiza que nunca haya dos. El profesor debe
     * estar aprobado para enseñar; si no, 403 — un no-aprobado no recibe mensajes.
     */
    @Transactional
    public Conversation openAsStudent(User student, UUID professorId) {
        professorAccess.assertCanTeach(professorId);
        return conversations.findByStudentIdAndProfessorId(student.getId(), professorId)
                .orElseGet(() -> conversations.save(new Conversation(student.getId(), professorId)));
    }

    /**
     * La bandeja del usuario: sus conversaciones con la contraparte, el último mensaje y cuántos
     * lleva sin leer, ya ordenadas por actividad reciente.
     */
    @Transactional(readOnly = true)
    public List<ConversationView> inbox(User user) {
        return conversations.findAllForUser(user.getId()).stream()
                .map(conversation -> {
                    UUID counterpartId = conversation.counterpartOf(user.getId());
                    User counterpart = users.findById(counterpartId).orElse(null);
                    Message last = messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                            .stream().reduce((first, second) -> second).orElse(null);
                    int unread = messages.countUnread(conversation.getId(), user.getId());
                    return new ConversationView(conversation, counterpart, last, unread);
                })
                .toList();
    }

    /**
     * Los mensajes de una conversación, en orden, y de paso marca como leídos los que le llegaron
     * al lector (los de la contraparte o el sistema). Un tercero recibe 403.
     */
    @Transactional
    public List<Message> readMessages(User reader, UUID conversationId) {
        requireParticipant(conversationId, reader.getId());
        List<Message> thread = messages.findByConversationIdOrderByCreatedAtAsc(conversationId);
        thread.stream()
                .filter(message -> !message.isRead())
                .filter(message -> !reader.getId().equals(message.getSenderId()))
                .forEach(message -> message.markRead(clock.instant()));
        messages.saveAll(thread);
        return thread;
    }

    /**
     * Envía un mensaje. Aplica la política de contacto (enmascara y marca info de contacto), toca la
     * conversación y publica el evento que dispara notificación + correo AFTER_COMMIT. Solo un
     * participante puede escribir; un tercero recibe 403.
     */
    @Transactional
    public Message send(User sender, UUID conversationId, String rawBody) {
        Conversation conversation = requireParticipant(conversationId, sender.getId());

        MaskResult policy = applyContactPolicy(rawBody);
        String stored = policy.masked();
        String original = policy.wasMasked() ? rawBody : null;

        Message message = new Message(conversationId, sender.getId(), stored, original, policy.reason());
        Message saved = messages.save(message);

        conversation.touch(clock.instant());
        conversations.save(conversation);

        events.publishEvent(new MessagePostedEvent(saved.getId()));
        return saved;
    }

    /** El texto que ven las partes según el modo activo. Si no es MASK, no se toca nada. */
    private MaskResult applyContactPolicy(String rawBody) {
        if (!MASK_MODE.equalsIgnoreCase(settings.getString(CONTACT_POLICY_KEY).trim())) {
            return new MaskResult(rawBody, null);
        }
        return ContactMasker.mask(rawBody);
    }

    private Conversation requireParticipant(UUID conversationId, UUID userId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada"));
        if (!conversation.hasParticipant(userId)) {
            throw new ForbiddenException("Esta conversación no es tuya");
        }
        return conversation;
    }
}
