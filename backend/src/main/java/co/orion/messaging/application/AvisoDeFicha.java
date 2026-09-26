package co.orion.messaging.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.domain.FichaPorCompletarEvent;
import co.orion.shared.text.ListaEnPalabras;

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
        String titulo = event.paso() == 1
                ? "Rigel: completa tu ficha y gana «Ficha completa»"
                : "Tu ficha sigue a medias: "
                        + (event.faltan().size() == 1 ? ListaEnPalabras.teFalta(event.faltan()) : "te falta poco");
        String cuerpo = ListaEnPalabras.teFaltaAlComienzo(event.faltan())
                + ". Con tu ficha completa los profes"
                + " preparan tu clase sabiendo qué buscas. +25 puntos.";
        notifications.create(event.studentId(), "PROFILE_NUDGE", titulo, cuerpo, "/cuenta?seccion=ficha");
    }
}
