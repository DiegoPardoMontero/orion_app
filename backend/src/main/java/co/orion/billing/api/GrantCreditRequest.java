package co.orion.billing.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Un crédito otorgado a mano por el admin. {@code reason} es de la lista cerrada de
 * {@code CreditReason}: un saldo sin motivo trazable es un descuadre esperando a ser descubierto.
 */
public record GrantCreditRequest(@NotNull UUID studentId,
                                 @NotNull @Positive Long amountCop,
                                 @NotNull String reason,
                                 UUID bookingId) {
}
