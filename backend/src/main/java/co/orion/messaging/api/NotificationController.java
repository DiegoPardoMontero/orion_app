package co.orion.messaging.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.messaging.application.NotificationService;
import co.orion.shared.security.OrionUserDetails;

/** Las notificaciones in-app del usuario autenticado. */
@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal OrionUserDetails principal) {
        return notifications.list(principal.user().getId()).stream()
                .map(NotificationResponse::of)
                .toList();
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal OrionUserDetails principal) {
        return new UnreadCountResponse(notifications.unreadCount(principal.user().getId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        notifications.markRead(principal.user().getId(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal OrionUserDetails principal) {
        notifications.markAllRead(principal.user().getId());
    }
}
