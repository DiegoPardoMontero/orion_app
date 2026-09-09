package co.orion.identity.api;

import jakarta.validation.constraints.NotNull;

/**
 * El switch del perfil público. No pide nada más: la mayoría de edad se declara en el registro y
 * Orión no guarda la fecha de nacimiento de nadie.
 */
public record StudentVisibilityRequest(@NotNull Boolean isPublic) {
}
