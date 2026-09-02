package co.orion.billing.application;

/** El estado de una transacción consultado directamente a la pasarela (conciliación y respaldo). */
public record ProviderTransaction(String transactionId,
                                  String reference,
                                  ProviderTransactionStatus status,
                                  long amountInCents) {
}
