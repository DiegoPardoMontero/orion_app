package co.orion.reputation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El admin oculta una reseña: el motivo es obligatorio y queda registrado en la fila. */
public record HideReviewRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 300, message = "El motivo no puede superar 300 caracteres")
        String reason) {
}
