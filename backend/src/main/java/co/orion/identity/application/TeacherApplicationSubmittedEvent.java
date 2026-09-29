package co.orion.identity.application;

import java.util.UUID;

/**
 * Se publica cuando un aspirante envía su postulación o la reenvía con los cambios pedidos. El correo
 * al equipo sale AFTER_COMMIT: sin él, que una postulación llegara solo se veía abriendo el panel, y
 * el plan de lanzamiento pide revisarlas el mismo día (revisión del 28/09/2026).
 */
public record TeacherApplicationSubmittedEvent(UUID applicationId, String applicantName, boolean resubmitted) {
}
