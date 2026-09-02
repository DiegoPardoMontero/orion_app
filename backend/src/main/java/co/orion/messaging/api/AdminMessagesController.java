package co.orion.messaging.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.messaging.persistence.MessageRepository;

/** La cola de moderación: los mensajes que la política de contacto marcó, para que el admin los revise. */
@RestController
@RequestMapping("/api/v1/admin/messages")
public class AdminMessagesController {

    private final MessageRepository messages;

    public AdminMessagesController(MessageRepository messages) {
        this.messages = messages;
    }

    @GetMapping("/flagged")
    public List<FlaggedMessageResponse> flagged() {
        return messages.findByFlaggedReasonIsNotNullOrderByCreatedAtDesc().stream()
                .map(FlaggedMessageResponse::of)
                .toList();
    }
}
