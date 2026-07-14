package co.orion.shared.error;

/**
 * El usuario está autenticado pero no puede hacer esto (403). Se usa cuando la regla depende
 * de los datos y no solo del rol — p. ej. un estudiante reservando en nombre de otro.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
