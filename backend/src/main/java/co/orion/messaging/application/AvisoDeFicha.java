package co.orion.messaging.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.domain.FichaPorCompletarEvent;

/**
 * Los recordatorios de la ficha que van a la campana: el del día 1 y el del día 3 (el 2 es correo).
 * Hablan como Rigel y nombran lo que falta y el logro que se gana.
 */
@Component
public class AvisoDeFicha {

    private final NotificationService notifications;

    public AvisoDeFicha(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(FichaPorCompletarEvent event) {
        if (event.paso() == 2) {
            return;
        }
        String faltan = String.join(", ", event.faltan());
        String titulo = event.paso() == 1
                ? "Rigel: completa tu ficha y gana «Ficha completa»"
                : "Tu ficha sigue a medias: te falta " + (event.faltan().size() == 1 ? event.faltan().getFirst() : "poco");
        String cuerpo = "Te falta " + faltan + ". Con tu ficha completa —y visible, te lo recomiendo— los profes"
                + " preparan tu clase sabiendo qué buscas. +25 puntos.";
        notifications.create(event.studentId(), "PROFILE_NUDGE", titulo, cuerpo, "/cuenta?seccion=ficha");
    }
}
