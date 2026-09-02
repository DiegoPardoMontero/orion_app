package co.orion.billing.domain;

/**
 * Por qué Orión le debe plata a un estudiante. La lista es cerrada a propósito: un crédito sin
 * motivo trazable es un descuadre contable esperando a que alguien lo descubra.
 */
public enum CreditReason {

    PROFESSOR_NO_SHOW,
    CANCELLED_BY_PROFESSOR,
    DISPUTE_RESOLVED,
    ADMIN_ADJUSTMENT
}
