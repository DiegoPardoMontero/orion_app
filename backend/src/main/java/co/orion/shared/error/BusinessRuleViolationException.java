package co.orion.shared.error;

/** Regla de negocio violada. El manejador global la traduce a 400 con {"error": mensaje}. */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
