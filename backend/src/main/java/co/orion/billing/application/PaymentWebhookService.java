package co.orion.billing.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.Payment;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.scheduling.application.BookingService;

/**
 * Lo que llega de la pasarela. El orden de las tres operaciones no es casual:
 *
 * <ol>
 *   <li><b>Verificar la firma</b> antes de tocar la base. Un webhook sin verificar es un endpoint
 *       público que confirma reservas gratis.</li>
 *   <li><b>Guardar el payload crudo</b> antes de interpretarlo, en su propia transacción. Si la
 *       lógica falla, el hecho no se pierde y se puede reprocesar. El UNIQUE
 *       (provider, provider_event_id) es lo que garantiza que un reenvío —la pasarela reenvía, es
 *       normal— no se procese dos veces.</li>
 *   <li><b>Aplicarlo</b> al pago y a la reserva.</li>
 * </ol>
 *
 * Un evento de una transacción que no conocemos se registra y se descarta sin explotar: puede ser
 * una prueba desde el panel de Wompi, y responder 500 solo haría que lo reintenten para siempre.
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentProvider provider;
    private final PaymentRepository payments;
    private final PaymentEventRecorder recorder;
    private final BookingService bookings;
    private final CreditService credits;
    private final Clock clock;

    public PaymentWebhookService(PaymentProvider provider,
                                 PaymentRepository payments,
                                 PaymentEventRecorder recorder,
                                 BookingService bookings,
                                 CreditService credits,
                                 Clock clock) {
        this.provider = provider;
        this.payments = payments;
        this.recorder = recorder;
        this.bookings = bookings;
        this.credits = credits;
        this.clock = clock;
    }

    /** Devuelve false si el evento ya se había procesado (reenvío): el llamador responde 200 igual. */
    @Transactional
    public boolean handle(String rawBody, Map<String, String> headers) {
        ProviderEvent event = provider.parseWebhook(rawBody, headers);   // 1. firma, o 401

        Optional<Payment> payment = event.reference() == null
                ? Optional.<Payment>empty()
                : payments.findByProviderAndProviderReference(event.provider(), event.reference());

        UUID paymentId = payment.map(Payment::getId).orElse(null);
        if (!recorder.record(event, paymentId)) {                        // 2. hecho crudo
            log.info("Webhook {} reenviado: ya estaba procesado", event.eventId());
            return false;
        }

        if (payment.isEmpty()) {
            log.warn("Webhook {} de una transacción desconocida (referencia {})",
                    event.eventId(), event.reference());
            return true;
        }
        apply(event, payment.get());                                     // 3. efecto
        return true;
    }

    /**
     * Le pregunta a la pasarela por una transacción concreta y aplica lo que responda. Es la red de
     * seguridad para el webhook que no llega: sin esto, un evento perdido deja al estudiante con el
     * cobro hecho en Wompi y la reserva vencida, y nadie en Orión se entera.
     *
     * Lo dispara la pantalla de retorno, que recibe el id de transacción en la propia URL de vuelta.
     * Ese id viene del navegador, así que NO se cree por sí solo: solo se aplica si la transacción
     * que devuelve Wompi referencia a este mismo pago. Sin esa comprobación, cualquiera podría
     * confirmar su reserva pasando el id de una transacción aprobada ajena.
     *
     * Registra el hecho con la misma clave de idempotencia que usaría el webhook, así que si el
     * evento llega después no se procesa dos veces.
     */
    @Transactional
    public void syncFromProvider(Payment payment, String transactionId) {
        if (!payment.isPending()) {
            return;
        }
        ProviderTransaction transaction = provider.fetchTransaction(transactionId);

        if (!Objects.equals(transaction.reference(), payment.getProviderReference())) {
            log.warn("Transacción {} consultada para el pago {} pero su referencia es {}",
                    transactionId, payment.getId(), transaction.reference());
            return;
        }

        ProviderEvent event = new ProviderEvent(
                provider.name(),
                transaction.transactionId() + ":" + transaction.status(),
                "transaction.synced",
                transaction.transactionId(),
                transaction.reference(),
                transaction.status(),
                transaction.amountInCents(),
                "{\"source\":\"sync\",\"transactionId\":\"" + transaction.transactionId() + "\"}");

        if (!recorder.record(event, payment.getId())) {
            return;   // el webhook ya había traído este mismo hecho
        }
        apply(event, payment);
    }

    private void apply(ProviderEvent event, Payment payment) {
        switch (event.status()) {
            case APPROVED -> approve(payment, event);
            case DECLINED, VOIDED, ERROR -> decline(payment);
            case PENDING -> log.info("Pago {} sigue pendiente en la pasarela", payment.getId());
        }
    }

    private void approve(Payment payment, ProviderEvent event) {
        if (!payment.isPending()) {
            return;   // ya estaba aprobado: reenvío con otro id de evento, nada que hacer
        }
        // El monto que aprobó la pasarela tiene que ser el que pedimos. Si no cuadra, no se
        // confirma nada: un pago por menos de lo debido es un incidente, no una clase.
        long expectedCents = payment.getChargedCop() * 100;
        if (event.amountInCents() != expectedCents) {
            log.error("Pago {}: la pasarela aprobó {} centavos y esperábamos {}",
                    payment.getId(), event.amountInCents(), expectedCents);
            return;
        }
        payment.markPaid(event.provider(), payment.getProviderReference(), clock.instant());
        payments.save(payment);
        bookings.confirmPaid(payment.getBookingId());
    }

    private void decline(Payment payment) {
        if (!payment.isPending()) {
            return;
        }
        payment.cancel();
        payments.save(payment);
        credits.restore(payment.getId());
        bookings.expirePendingPayment(payment.getBookingId());
    }
}
