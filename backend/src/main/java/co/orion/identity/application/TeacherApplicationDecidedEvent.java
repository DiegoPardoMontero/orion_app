package co.orion.identity.application;

/**
 * Se publica cuando el admin decide una postulación. El correo sale AFTER_COMMIT, así una decisión
 * confirmada nunca se queda sin aviso y un fallo de correo nunca revierte la decisión.
 */
public record TeacherApplicationDecidedEvent(String toEmail, Decision decision, String note) {

    public enum Decision {
        APPROVED,
        CHANGES_REQUESTED,
        REJECTED
    }
}
