package co.orion.support.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import co.orion.support.application.SupportService;

/** El ticket con su conversación. `mine` dice si un mensaje lo escribió quien está mirando. */
public record TicketThread(TicketSummary ticket, List<Message> messages) {

    public record Message(UUID authorId, boolean mine, String body, Instant createdAt) {
    }

    public static TicketThread from(SupportService.Hilo hilo, UUID viewerId, Instant now) {
        return new TicketThread(
                TicketSummary.from(hilo.ticket(), now),
                hilo.mensajes().stream()
                        .map(m -> new Message(m.getAuthorId(),
                                m.getAuthorId().equals(viewerId),
                                m.getBody(),
                                m.getCreatedAt()))
                        .toList());
    }
}
