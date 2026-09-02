package co.orion.reputation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El profesor reporta una reseña para revisión: el motivo es obligatorio. */
public record ReportReviewRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 300, message = "El motivo no puede superar 300 caracteres")
        String reason) {
}
