package co.orion.billing.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.PaymentEvent;
import co.orion.billing.persistence.PaymentEventRepository;
import co.orion.billing.persistence.PaymentRepository;

/**
 * Lo que tiene que sobrevivir a un rollback del procesamiento: el hecho crudo de la pasarela y, si
 * ese procesamiento falla, la marca de que ese pago quedó a medias.
 *
 * Es un bean aparte y no unos métodos de {@code PaymentWebhookService} por una razón concreta:
 * {@code REQUIRES_NEW} lo aplica el proxy de Spring, y una llamada a un método propio no pasa por
 * el proxy — quedaría dentro de la transacción que precisamente queremos sobrevivir.
 */
@Service
public class PaymentEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventRecorder.class);

    private final PaymentEventRepository events;
    private final PaymentRepository payments;

    public PaymentEventRecorder(PaymentEventRepository events, PaymentRepository payments) {
        this.events = events;
        this.payments = payments;
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

    /**
     * Marca un pago para revisión humana en una transacción propia, para que la marca sobreviva al
     * rollback del intento que falló.
     *
     * Sin esto había un agujero silencioso: el evento se guarda con {@code REQUIRES_NEW} y por
     * tanto sobrevive, pero si el procesamiento revienta y hace rollback, el reintento de la
     * pasarela encuentra el evento ya registrado, lo declara duplicado y no vuelve a intentarlo.
     * El cobro quedaba hecho, la reserva sin confirmar y nadie enterado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void flagForReview(UUID paymentId) {
        if (paymentId == null) {
            return;
        }
        payments.findById(paymentId).ifPresent(payment -> {
            try {
                payment.flagForReview();
                payments.save(payment);
            } catch (IllegalStateException ex) {
                // El pago ya estaba resuelto (pagado, devuelto). No hay incidente que marcar.
                log.warn("No se marcó el pago {} para revisión: {}", paymentId, ex.getMessage());
            }
        });
    }
}
