package co.orion.billing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.application.TeacherApplicationDecidedEvent;

/**
 * La llave Bre-B se pide en la postulación (29/09/2026); si la postulación se rechaza, se borra. Va
 * por evento, como el resto de lo que reacciona a una decisión: {@code identity} no sabe que existen
 * los datos de pago.
 */
@Component
class DatosDePagoDelRechazado {

    private static final Logger log = LoggerFactory.getLogger(DatosDePagoDelRechazado.class);

    private final PayoutDetailsService details;

    DatosDePagoDelRechazado(PayoutDetailsService details) {
        this.details = details;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(TeacherApplicationDecidedEvent event) {
        if (event.decision() != TeacherApplicationDecidedEvent.Decision.REJECTED) {
            return;
        }
        try {
            details.forget(event.userId());
        } catch (RuntimeException ex) {
            log.error("No se pudieron borrar los datos de pago del aspirante rechazado {}", event.userId(), ex);
        }
    }
}
