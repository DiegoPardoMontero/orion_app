package co.orion.identity.api;

/** Motivo de una decisión que lo exige (rechazo o petición de cambios): ≥10 caracteres. */
public record ReviewDecisionRequest(String note) {
}
