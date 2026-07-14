package co.orion.shared.error;

/**
 * La petición está bien formada pero el negocio no la puede procesar (422): el cupo no está
 * disponible, el estudiante ya tiene clase a esa hora, faltan menos de 24 h para cancelar.
 * Distinto de un 400: aquí no hay nada mal escrito, hay algo que no se puede hacer.
 */
public class UnprocessableException extends RuntimeException {

    public UnprocessableException(String message) {
        super(message);
    }
}
