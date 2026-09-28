package co.orion.billing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.billing.domain.PayoutDetailsChangedEvent;

/**
 * En cuanto el profe registra (o cambia) sus datos de pago, su liquidación retenida por «Faltan los
 * datos de pago» vuelve a borrador. Antes solo se recalculaba cuando un admin abría /admin/pagos.
 *
 * <p>{@code AFTER_COMMIT} + {@code REQUIRES_NEW}, como en {@link BookingBillingListener}: se recalcula
 * con los datos ya guardados, y un fallo aquí no tumba el registro de los datos (el admin y la vista
 * del profe lo vuelven a recalcular al leer).
 */
@Component
public class PayoutHoldsListener {

    private static final Logger log = LoggerFactory.getLogger(PayoutHoldsListener.class);

    private final PayoutService payouts;

    public PayoutHoldsListener(PayoutService payouts) {
        this.payouts = payouts;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PayoutDetailsChangedEvent event) {
        try {
            payouts.refreshHoldsOf(event.professorId());
        } catch (RuntimeException ex) {
            log.error("No se pudo recalcular la retención de las liquidaciones del profe {}",
                    event.professorId(), ex);
        }
    }
}
