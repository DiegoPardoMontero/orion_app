package co.orion.shared.error;

/** Choque con el estado actual del sistema (409): transición inválida, carrera perdida. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
