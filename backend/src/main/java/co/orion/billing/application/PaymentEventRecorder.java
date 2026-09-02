package co.orion.billing.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.PaymentEvent;
import co.orion.billing.persistence.PaymentEventRepository;

/**
 * Guarda el hecho crudo de la pasarela en su propia transacción.
 *
 * Es un bean aparte y no un método de {@code PaymentWebhookService} por una razón concreta:
 * {@code REQUIRES_NEW} lo aplica el proxy de Spring, y una llamada a un método propio no pasa por
 * el proxy. Separado, el commit del evento ocurre de verdad antes de procesarlo, y si el
 * procesamiento revienta el hecho ya está a salvo para reprocesarlo.
 */
@Service
public class PaymentEventRecorder {

    private final PaymentEventRepository events;

    public PaymentEventRecorder(PaymentEventRepository events) {
        this.events = events;
    }

    /** Devuelve false si el evento ya estaba registrado: esa es la idempotencia del webhook. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean record(ProviderEvent event, UUID paymentId) {
        if (events.existsByProviderAndProviderEventId(event.provider(), event.eventId())) {
            return false;
        }
        events.save(new PaymentEvent(
                paymentId,
                event.provider(),
                event.eventId(),
                event.eventType(),
                event.rawPayload()));
        return true;
    }
}
