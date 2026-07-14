package co.orion.shared.error;

/**
 * Recurso inexistente — o ajeno. Pedir un recurso de otro profesor responde 404 y no 403:
 * un 403 confirmaría que el recurso existe, que es justo lo que no queremos filtrar.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
