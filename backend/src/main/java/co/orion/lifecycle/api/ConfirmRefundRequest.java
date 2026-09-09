package co.orion.lifecycle.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirmar una devolución hecha en Wompi. La referencia es obligatoria: marcar como pagado sin
 * poder señalar el movimiento convierte el registro en una afirmación, y en una devolución legal
 * la afirmación es justo lo que no basta.
 */
public record ConfirmRefundRequest(
        @NotBlank(message = "Escribe la referencia de la devolución que hiciste en Wompi.")
        @Size(max = 140) String reference,
        @Size(max = 2000) String note) {
}
