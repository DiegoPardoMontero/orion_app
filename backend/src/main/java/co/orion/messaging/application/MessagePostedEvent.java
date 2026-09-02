package co.orion.messaging.application;

import java.util.UUID;

/**
 * Se publica al enviar un mensaje de una persona. El listener lo convierte, AFTER_COMMIT, en una
 * notificación in-app y un correo para la contraparte. El servicio de mensajería no sabe que existen
 * las notificaciones ni los correos: se entera por este evento, igual que las reservas.
 */
public record MessagePostedEvent(UUID messageId) {
}
