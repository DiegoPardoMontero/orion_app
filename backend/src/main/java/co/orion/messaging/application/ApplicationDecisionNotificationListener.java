package co.orion.messaging.application;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.application.TeacherApplicationDecidedEvent;

/**
 * Bloque 3 (bonus): además del correo, una decisión sobre la postulación deja una notificación
 * in-app. AFTER_COMMIT y @Async como el resto: si la decisión hizo rollback no hay aviso falso.
 * Reutiliza el evento que ya publicaba identity, sin acoplar aquel módulo a las notificaciones.
 */
@Component
public class ApplicationDecisionNotificationListener {

    private final NotificationService notifications;

    public ApplicationDecisionNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(TeacherApplicationDecidedEvent event) {
        String linkPath = "/postulacion";
        switch (event.decision()) {
            case APPROVED -> notifications.create(event.userId(), "APPLICATION_APPROVED",
                    "¡Tu postulación fue aprobada!",
                    "Ya puedes publicar tu perfil y recibir clases en Orión.", linkPath);
            case CHANGES_REQUESTED -> notifications.create(event.userId(), "APPLICATION_CHANGES_REQUESTED",
                    "Tu postulación necesita cambios",
                    event.note(), linkPath);
            case REJECTED -> notifications.create(event.userId(), "APPLICATION_REJECTED",
                    "Novedades sobre tu postulación",
                    event.note(), linkPath);
        }
    }
}
