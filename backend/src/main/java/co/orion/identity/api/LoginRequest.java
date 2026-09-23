package co.orion.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import co.orion.shared.security.CabeEnBcrypt;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank @CabeEnBcrypt String password) {
}
