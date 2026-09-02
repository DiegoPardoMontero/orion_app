package co.orion.billing.application;

import java.time.Clock;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.CreditReason;
import co.orion.billing.domain.Payment;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.scheduling.application.BookingService;
import co.orion.shared.error.ResourceNotFoundException;

import java.util.UUID;

/**
 * Las transiciones del dinero que NO vienen de la pasarela: la reserva venció, la clase se dictó,
 * el profesor canceló. Cada una mueve el pago y, cuando toca, le devuelve algo al estudiante.
 */
@Service
public class PaymentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(PaymentLifecycleService.class);

    private final PaymentRepository payments;
    private final CreditService credits;
    private final BookingService bookings;
    private final Clock clock;

    public PaymentLifecycleService(PaymentRepository payments,
                                   CreditService credits,
                                   BookingService bookings,
                                   Clock clock) {
        this.payments = payments;
        this.credits = credits;
        this.bookings = bookings;
        this.clock = clock;
    }

    /**
     * Anula un cobro que nunca llegó a ocurrir y devuelve intacto el crédito que se había gastado:
     * a sus filas originales, con su motivo y su vencimiento. No toca la reserva.
     */
    @Transactional
    public void cancelUnpaidOnly(UUID bookingId) {
        payments.findByBookingId(bookingId)
                .filter(Payment::isPending)
                .ifPresent(payment -> {
                    payment.cancel();
                    payments.save(payment);
                    credits.restore(payment.getId());
                });
    }

    /**
     * Nadie pagó a tiempo: se anula el cobro Y se libera el cupo. Es lo que hace el job de
     * expiración, que a diferencia de una cancelación es quien tiene que mover también la reserva.
     */
    @Transactional
    public void cancelUnpaid(UUID bookingId) {
        cancelUnpaidOnly(bookingId);
        bookings.expirePendingPayment(bookingId);
    }

    /**
     * La clase se dictó: el dinero deja de estar retenido y el profesor puede cobrarlo en la
     * siguiente liquidación.
     *
     * Un NO_SHOW del estudiante también libera: el profesor reservó su hora y estuvo ahí. Quien no
     * cobra es el profesor que no aparece, y ese caso (con su crédito al estudiante) es del Bloque 5.
     */
    @Transactional
    public void release(UUID bookingId) {
        payments.findByBookingId(bookingId)
                .filter(Payment::isPaid)
                .ifPresent(payment -> {
                    payment.release(clock.instant());
                    payments.save(payment);
                });
    }

    /**
     * La clase no va a ocurrir por decisión del profesor o de la administración. El estudiante
     * recupera el valor COMPLETO de la clase como saldo —lo que puso de su bolsillo y lo que puso
     * de crédito—, porque eso es exactamente lo que consumió al reservar.
     *
     * No se devuelve por la pasarela: Wompi no expone reembolso por API. Si el estudiante quiere su
     * plata de vuelta y no un saldo, el admin la devuelve desde el panel de Wompi y esta fila queda
     * como el registro de que se le compensó.
     */
    @Transactional
    public void refundToCredit(UUID bookingId, CreditReason reason, UUID actorId) {
        Optional<Payment> found = payments.findByBookingId(bookingId);
        if (found.isEmpty()) {
            return;
        }
        Payment payment = found.get();
        if (payment.isPending()) {
            cancelUnpaidOnly(bookingId);
            return;
        }
        if (!payment.isPaid()) {
            log.info("Pago {} no admite devolución (estado {})", payment.getId(), payment.getStatus());
            return;
        }
        payment.refund(clock.instant());
        payments.save(payment);
        credits.grant(payment.getStudentId(), payment.getAmountCop(), reason, bookingId, null, actorId);
    }

    @Transactional(readOnly = true)
    public Payment ofBooking(UUID bookingId) {
        return payments.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("La reserva no tiene pago asociado"));
    }
}
