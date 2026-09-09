package co.orion.billing.domain;

/**
 * Por qué Orión le debe plata a un estudiante. La lista es cerrada a propósito: un crédito sin
 * motivo trazable es un descuadre contable esperando a que alguien lo descubra.
 */
public enum CreditReason {

    PROFESSOR_NO_SHOW,
    CANCELLED_BY_PROFESSOR,

    /**
     * El propio estudiante canceló estando dentro de plazo. Motivo aparte del anterior porque la
     * conciliación necesita distinguir quién soltó la clase: mezclarlos haría parecer que un
     * profesor cancela mucho más de lo que cancela.
     */
    CANCELLED_BY_STUDENT,

    DISPUTE_RESOLVED,
    ADMIN_ADJUSTMENT
}
