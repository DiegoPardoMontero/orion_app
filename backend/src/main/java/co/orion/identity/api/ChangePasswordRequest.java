package co.orion.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword es obligatoria")
        String currentPassword,

        @NotBlank
        @Size(min = 8, message = "La contraseña nueva debe tener al menos 8 caracteres")
        String newPassword) {
}
