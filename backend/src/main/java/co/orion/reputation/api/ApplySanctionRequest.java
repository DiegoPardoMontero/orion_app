package co.orion.reputation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El motivo es obligatorio: una sanción sin explicación no corrige nada, solo aleja a alguien. */
public record ApplySanctionRequest(@NotBlank String type,
                                   @NotBlank @Size(max = 300) String reason) {
}
