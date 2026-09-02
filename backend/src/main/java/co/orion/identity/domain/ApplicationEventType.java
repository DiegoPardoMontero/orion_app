package co.orion.identity.domain;

/** Cada evento de la bitácora de una postulación. Toda transición escribe uno, sin excepción. */
public enum ApplicationEventType {
    CREATED,
    SUBMITTED,
    REVIEW_STARTED,
    CHANGES_REQUESTED,
    RESUBMITTED,
    APPROVED,
    REJECTED
}
