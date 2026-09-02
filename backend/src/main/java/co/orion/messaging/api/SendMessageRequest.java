package co.orion.messaging.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El cuerpo de un mensaje. Acotado a algo razonable para un chat, no a un ensayo. */
public record SendMessageRequest(@NotBlank @Size(max = 4000) String body) {
}
