package co.orion.support.api;

import java.util.UUID;

import co.orion.support.domain.SupportMessage;
import co.orion.support.domain.SupportTicket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Abrir una solicitud. `bookingId` es opcional: solo cuando se habla de una clase concreta. */
public record OpenTicketRequest(
        @NotNull String category,
        @NotBlank @Size(max = SupportTicket.MAX_ASUNTO) String subject,
        @NotBlank @Size(max = SupportMessage.MAX_CUERPO) String body,
        UUID bookingId) {
}
