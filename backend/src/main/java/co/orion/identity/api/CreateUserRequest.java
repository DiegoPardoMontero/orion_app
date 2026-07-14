package co.orion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 20) String whatsappPhone,
        @NotBlank String role,
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password) {
}
