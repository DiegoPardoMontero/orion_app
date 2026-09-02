package co.orion.billing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La referencia de la transferencia es obligatoria: una liquidación marcada como pagada sin número
 * es una afirmación que nadie puede contrastar con el extracto del banco.
 */
public record MarkPayoutPaidRequest(@NotBlank @Size(max = 140) String reference) {
}
