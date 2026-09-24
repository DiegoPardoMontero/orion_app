package co.orion.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import co.orion.shared.security.CabeEnBcrypt;

public record ChangePasswordRequest(
        // Obligatoria para quien ya tiene contraseña; quien entró con Google crea la primera sin ella
        // (lo decide PasswordService, que sabe cuál es el caso).
        @CabeEnBcrypt String currentPassword,

        @NotBlank
        @Size(min = 8, message = "La contraseña nueva debe tener al menos 8 caracteres")
        @CabeEnBcrypt String newPassword) {
}
