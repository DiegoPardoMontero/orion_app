package co.orion.catalog.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Puede venir vacío: un enlace vacío quiere decir «no hay» (el video de bienvenida). Para los
 * demás tipos, el vacío lo rechaza {@code SettingDefinition} con un 422 que dice qué ajuste es.
 */
public record UpdateSettingRequest(@NotNull @Size(max = 500) String value) {
}
