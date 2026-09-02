package co.orion.messaging.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.messaging.domain.Notification;
import co.orion.messaging.persistence.NotificationRepository;
import co.orion.shared.error.ResourceNotFoundException;

/** Las notificaciones in-app: crear, listar, contar y marcar leídas. */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public Notification create(UUID userId, String type, String title, String body, String linkPath) {
        return notifications.save(new Notification(userId, type, title, body, linkPath));
    }

    @Transactional(readOnly = true)
    public List<Notification> list(UUID userId) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /** Marca una notificación como leída. Ajena → 404: no confirmamos que exista. */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada"));
        notification.markRead(clock.instant());
        notifications.save(notification);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        List<Notification> unread = notifications.findByUserIdAndReadAtIsNull(userId);
        unread.forEach(notification -> notification.markRead(clock.instant()));
        notifications.saveAll(unread);
    }
}
