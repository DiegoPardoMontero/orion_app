package co.orion.billing.domain;

/** Los tipos de llave de Bre-B que un profe puede registrar para recibir sus pagos. */
public enum BreBKeyType {

    /** Su celular: diez dígitos que empiezan por 3. */
    PHONE("Celular"),

    /** Su número de cédula. */
    ID_NUMBER("Cédula"),

    /** Su correo. */
    EMAIL("Correo"),

    /** Una llave alfanumérica, la que empieza por @. */
    ALPHANUMERIC("Alfanumérica");

    private final String etiqueta;

    BreBKeyType(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
