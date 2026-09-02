package co.orion.billing.application;

/**
 * Los estados que una pasarela puede reportar, normalizados. Wompi usa exactamente estos nombres;
 * si algún día entra otra pasarela, su adaptador traduce a este vocabulario y el dominio no se entera.
 */
public enum ProviderTransactionStatus {

    PENDING,
    APPROVED,
    DECLINED,
    VOIDED,
    ERROR;

    /** DECLINED, VOIDED y ERROR son todos "esta plata no llegó y no va a llegar". */
    public boolean isFinalFailure() {
        return this == DECLINED || this == VOIDED || this == ERROR;
    }
}
