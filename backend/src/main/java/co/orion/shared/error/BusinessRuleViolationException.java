package co.orion.shared.error;

import java.util.List;

/**
 * Regla de negocio violada. El manejador global la traduce a 400 con {"error": mensaje}. Puede
 * llevar además una lista de detalles (p. ej. todos los requisitos que faltan para enviar una
 * postulación), que el manejador expone como {"missing": [...]} en un solo 400.
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final List<String> details;

    public BusinessRuleViolationException(String message) {
        super(message);
        this.details = List.of();
    }

    public BusinessRuleViolationException(String message, List<String> details) {
        super(message);
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }
}
