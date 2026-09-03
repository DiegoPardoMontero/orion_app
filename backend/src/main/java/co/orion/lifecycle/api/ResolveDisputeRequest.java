package co.orion.lifecycle.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La nota es obligatoria y no es burocracia: esta decisión mueve dinero real, y alguien tiene que
 * poder entender dentro de seis meses por qué se tomó.
 */
public record ResolveDisputeRequest(@NotBlank String outcome,
                                    @NotBlank @Size(max = 1000) String note) {
}
