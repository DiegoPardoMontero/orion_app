package co.orion.billing.domain;

/** Estado de una liquidación. La transferencia la hace una persona; aquí solo se registra. */
public enum PayoutStatus {

    PENDING,
    PAID,
    CANCELLED
}
