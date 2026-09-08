package co.orion.identity.api;

import jakarta.validation.constraints.NotBlank;

/** El token del enlace de verificación. Viaja en el cuerpo, no en la URL: no queda en logs. */
public record VerifyEmailRequest(@NotBlank String token) {
}
