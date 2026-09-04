package co.orion.identity.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * El switch del perfil público. La fecha de nacimiento solo hace falta para activarlo, y solo la
 * primera vez: pedirla en el registro sería cobrarle el dato a todo el mundo por una función que
 * casi nadie va a usar.
 */
public record StudentVisibilityRequest(@NotNull Boolean isPublic, LocalDate birthDate) {
}
