package co.orion.identity.application;

import java.util.UUID;

/**
 * Se publica cuando el admin decide una postulación. El correo (y desde el Bloque 3, la notificación
 * in-app) salen AFTER_COMMIT, así una decisión confirmada nunca se queda sin aviso y un fallo de
 * correo nunca revierte la decisión. Lleva {@code userId} para poder crear la notificación in-app.
 */
public record TeacherApplicationDecidedEvent(UUID userId, String toEmail, Decision decision, String note) {

    public enum Decision {
        APPROVED,
        CHANGES_REQUESTED,
        REJECTED
    }
}
