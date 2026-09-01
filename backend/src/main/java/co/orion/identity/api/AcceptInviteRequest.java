package co.orion.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lo que el profesor completa al aceptar la invitación: sus datos y su contraseña. */
public record AcceptInviteRequest(
        @NotBlank String token,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,
        @Size(max = 20) String whatsappPhone,
        @Size(max = 120) String headline,
        String bio) {
}
