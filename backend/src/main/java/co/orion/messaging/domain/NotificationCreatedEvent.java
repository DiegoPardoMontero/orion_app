package co.orion.messaging.domain;

import java.util.UUID;

/**
 * Entró un aviso a la campana de alguien. Lo escucha el aviso en el dispositivo, que decide si
 * además suena; {@code messaging} no sabe que existen los navegadores suscritos.
 */
public record NotificationCreatedEvent(UUID userId, String type, String title, String body, String linkPath) {
}
