package co.orion.identity.application;

import java.util.UUID;

/**
 * Se publica cuando un aspirante abre su postulación por primera vez: es el momento en que «un profe
 * nuevo se registró» (Pardo, 15/10/2026: «para no perder ninguno»). El correo al equipo y la
 * notificación del admin salen AFTER_COMMIT, como con la postulación enviada.
 */
public record TeacherApplicationStartedEvent(UUID applicationId, UUID userId, String applicantName, String applicantEmail) {
}
