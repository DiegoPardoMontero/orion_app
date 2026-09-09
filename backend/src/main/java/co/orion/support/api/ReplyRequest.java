package co.orion.support.api;

import co.orion.support.domain.SupportMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un mensaje más en el hilo. */
public record ReplyRequest(@NotBlank @Size(max = SupportMessage.MAX_CUERPO) String body) {
}
