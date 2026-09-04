package co.orion.shared.error;

/**
 * Algo de lo que Orión depende no está disponible ahora mismo: una integración sin configurar, un
 * proveedor caído. No es culpa de quien lo pidió, y por eso no es un 4xx — pero tampoco es un fallo
 * inesperado del que no se pueda decir nada, y por eso no es el 500 genérico.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
