package co.orion.lifecycle.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El reclamo del estudiante. El motivo es de una lista cerrada; la descripción es libre. */
public record ReportProblemRequest(@NotBlank String reason,
                                   @Size(max = 1000) String description) {
}
