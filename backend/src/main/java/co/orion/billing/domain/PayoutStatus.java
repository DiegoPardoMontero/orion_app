package co.orion.billing.domain;

/**
 * Estado de una liquidación quincenal (brief de liquidaciones, regla 11). La transferencia la hace
 * una persona por Bre-B; aquí se registra.
 */
public enum PayoutStatus {

    /** Borrador: la creó el corte y el admin la puede revisar o regenerar. */
    DRAFT("Borrador"),

    /** Aprobada: lista para transferir. Ya no se regenera. */
    APPROVED("Aprobada"),

    /** Pagada: la transferencia se hizo y quedó registrada. Inmutable. */
    PAID("Pagada"),

    /** Retenida: falta algo del profe (el acuerdo, los datos de pago). Al resolverse vuelve a borrador. */
    ON_HOLD("Retenida"),

    /** Quedó en cero o en negativo: no se paga, y su saldo pasa a la siguiente como arrastre. */
    CARRIED_OVER("Sin pago: pasa a la siguiente");

    private final String etiqueta;

    PayoutStatus(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
