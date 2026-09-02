package co.orion.messaging.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.messaging.application.ConversationService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/** Estudiante <-> Orión <-> Profesor: el hilo de mensajes que reemplaza a WhatsApp. */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversations;

    public ConversationController(ConversationService conversations) {
        this.conversations = conversations;
    }

    /** El estudiante abre (o reencuentra) su conversación con un profesor aprobado. */
    @PostMapping
    public ConversationSummaryResponse open(@AuthenticationPrincipal OrionUserDetails principal,
                                            @Valid @RequestBody CreateConversationRequest body) {
        var conversation = conversations.openAsStudent(principal.user(), body.professorId());
        // Recién abierta o no, la devolvemos con la forma de la bandeja para que el frontend
        // navegue directo; los no leídos serán 0 si acaba de crearse.
        return conversations.inbox(principal.user()).stream()
                .filter(view -> view.conversation().getId().equals(conversation.getId()))
                .map(view -> ConversationSummaryResponse.of(view, principal.user().getId()))
                .findFirst()
                .orElseThrow();
    }

    @GetMapping
    public List<ConversationSummaryResponse> list(@AuthenticationPrincipal OrionUserDetails principal) {
        UUID viewerId = principal.user().getId();
        return conversations.inbox(principal.user()).stream()
                .map(view -> ConversationSummaryResponse.of(view, viewerId))
                .toList();
    }

    @GetMapping("/{id}/messages")
    public List<MessageResponse> messages(@AuthenticationPrincipal OrionUserDetails principal,
                                          @PathVariable UUID id) {
        UUID viewerId = principal.user().getId();
        return conversations.readMessages(principal.user(), id).stream()
                .map(message -> MessageResponse.of(message, viewerId))
                .toList();
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@AuthenticationPrincipal OrionUserDetails principal,
                                @PathVariable UUID id,
                                @Valid @RequestBody SendMessageRequest body) {
        var message = conversations.send(principal.user(), id, body.body());
        return MessageResponse.of(message, principal.user().getId());
    }
}
