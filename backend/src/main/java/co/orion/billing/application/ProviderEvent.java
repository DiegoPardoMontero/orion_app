package co.orion.billing.application;

/**
 * Un evento de la pasarela ya verificado y traducido. {@code eventId} es la clave de idempotencia:
 * el mismo hecho reenviado tiene el mismo id y se procesa una sola vez.
 */
public record ProviderEvent(String provider,
                            String eventId,
                            String eventType,
                            String transactionId,
                            String reference,
                            ProviderTransactionStatus status,
                            long amountInCents,
                            String rawPayload) {
}
