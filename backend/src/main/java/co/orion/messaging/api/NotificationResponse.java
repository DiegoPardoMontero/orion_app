package co.orion.messaging.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.messaging.domain.Notification;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String linkPath,
        boolean read,
        Instant createdAt,
        Instant readAt) {

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLinkPath(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }
}
