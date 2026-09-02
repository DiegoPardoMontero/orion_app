package co.orion.billing.api;

import java.util.List;

/** Saldo y detalle de los créditos vigentes de un estudiante. */
public record CreditBalanceResponse(long balanceCop, List<CreditResponse> credits) {
}
