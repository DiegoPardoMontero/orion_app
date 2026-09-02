package co.orion.billing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.billing.domain.CreditReason;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCancelledEvent;
import co.orion.scheduling.domain.BookingCompletedEvent;
import co.orion.scheduling.persistence.BookingRepository;

/**
 * El dinero reacciona a lo que pasa con la clase, igual que los correos: por evento y
 * {@code AFTER_COMMIT}. Así nunca se libera plata de una reserva que hizo rollback, y un fallo aquí
 * no tumba la cancelación ni el registro de asistencia que lo provocaron.
 *
 * {@code REQUIRES_NEW} no es opcional. En AFTER_COMMIT la transacción original sigue enlazada al
 * hilo pero ya está cerrándose: si estos métodos se unieran a ella, todo lo que escribieran se
 * descartaría SIN error — el pago se quedaría en PAID para siempre y nadie se enteraría. Con una
 * transacción nueva, el efecto se confirma de verdad.
 */
@Component
public class BookingBillingListener {

    private static final Logger log = LoggerFactory.getLogger(BookingBillingListener.class);

    private final PaymentLifecycleService payments;
    private final BookingRepository bookings;

    public BookingBillingListener(PaymentLifecycleService payments, BookingRepository bookings) {
        this.payments = payments;
        this.bookings = bookings;
    }

    /** La clase se dictó (o el estudiante no llegó): el profesor se ganó su parte. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCompleted(BookingCompletedEvent event) {
        try {
            payments.release(event.bookingId());
        } catch (RuntimeException ex) {
            log.error("No se pudo liberar el pago de la reserva {}", event.bookingId(), ex);
        }
    }

    /**
     * Quién canceló decide qué pasa con la plata:
     *
     * <ul>
     *   <li><b>El profesor o el admin</b>: la clase se cayó por causa nuestra. El estudiante
     *       recupera el valor completo como saldo, automáticamente.</li>
     *   <li><b>El estudiante, sin haber pagado</b>: no hay nada que devolver salvo el crédito que
     *       hubiera gastado, que vuelve a su sitio.</li>
     *   <li><b>El estudiante, habiendo pagado</b>: el pago se queda en PAID y NUNCA se libera —una
     *       reserva cancelada no llega a COMPLETED, así que jamás entra en una liquidación. Queda
     *       visible en la conciliación del admin para que decida entre saldo o devolución por el
     *       panel de Wompi. Es una decisión de política comercial, no técnica, y automatizarla
     *       aquí sería inventarla.</li>
     * </ul>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCancelled(BookingCancelledEvent event) {
        try {
            Booking booking = bookings.findById(event.bookingId()).orElse(null);
            if (booking == null) {
                return;
            }
            switch (booking.getStatus()) {
                case CANCELLED_BY_PROFESSOR, CANCELLED_BY_ADMIN -> payments.refundToCredit(
                        booking.getId(), CreditReason.CANCELLED_BY_PROFESSOR, booking.getCancelledBy());
                case CANCELLED_BY_STUDENT -> payments.cancelUnpaidOnly(booking.getId());
                default -> log.warn("Reserva {} cancelada en estado inesperado {}",
                        booking.getId(), booking.getStatus());
            }
        } catch (RuntimeException ex) {
            log.error("No se pudo ajustar el pago de la reserva cancelada {}", event.bookingId(), ex);
        }
    }
}
