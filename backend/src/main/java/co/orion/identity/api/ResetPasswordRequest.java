package co.orion.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El token del enlace del correo y la nueva contraseña. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String newPassword) {
}
