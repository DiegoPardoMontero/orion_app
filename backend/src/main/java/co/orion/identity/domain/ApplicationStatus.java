package co.orion.identity.domain;

/**
 * Ciclo de vida de una postulación a profesor. APPROVED y REJECTED son terminales: reintentar
 * tras un rechazo es una postulación NUEVA (el índice único parcial solo cubre los estados vivos).
 */
public enum ApplicationStatus {
    DRAFT,
    PENDING_REVIEW,
    UNDER_REVIEW,
    CHANGES_REQUESTED,
    APPROVED,
    REJECTED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }
}
