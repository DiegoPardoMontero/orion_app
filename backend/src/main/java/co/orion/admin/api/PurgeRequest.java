package co.orion.admin.api;

import jakarta.validation.constraints.NotBlank;

/**
 * La confirmación explícita. {@code confirm} tiene que traer el texto exacto que la vista previa
 * indica: no es burocracia, es la diferencia entre un clic y una decisión.
 */
public record PurgeRequest(@NotBlank String confirm, String reason) {
}
