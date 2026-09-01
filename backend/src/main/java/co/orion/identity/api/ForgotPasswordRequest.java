package co.orion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** El correo al que se enviaría el enlace de recuperación (si la cuenta existe). */
public record ForgotPasswordRequest(@NotBlank @Email String email) {
}
