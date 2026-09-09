package co.orion.billing.application;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.billing.domain.CreditReason;
import co.orion.scheduling.domain.Booking;
import co.orion.catalog.application.PlatformSettingsService;
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

    private static final String STUDENT_WINDOW = "student_cancel_hours";

    private final PaymentLifecycleService payments;
    private final BookingRepository bookings;
    private final PlatformSettingsService settings;

    public BookingBillingListener(PaymentLifecycleService payments, BookingRepository bookings,
                                  PlatformSettingsService settings) {
        this.payments = payments;
        this.bookings = bookings;
        this.settings = settings;
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
     *   <li><b>El estudiante, habiendo pagado</b>: <b>saldo a favor, automático</b>. Un estudiante
     *       depende de CUÁNDO. A tiempo (fuera de la ventana), saldo a favor automático. Tarde,
     *       la clase se considera prestada y el pago <b>se le libera al profesor</b>: apartó su
     *       hora y la perdió por un aviso de última hora. Antes esto quedaba esperando una
     *       decisión del admin; la decisión se tomó y está escrita en los Términos, que es lo que
     *       permite automatizarla sin inventar nada.</li>
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
                // Un retracto ya dejó el pago en REFUND_PENDING antes de llegar aquí, y ni
                // refundToCredit ni release tocan lo que no está en PAID: el dinero de un retracto
                // sigue su camino (medio de pago) y no acaba convertido en saldo ni en ingreso.
                case CANCELLED_BY_STUDENT -> resolverCancelacionDelEstudiante(booking);
                default -> log.warn("Reserva {} cancelada en estado inesperado {}",
                        booking.getId(), booking.getStatus());
            }
        } catch (RuntimeException ex) {
            log.error("No se pudo ajustar el pago de la reserva cancelada {}", event.bookingId(), ex);
        }
    }

    /**
     * Qué pasa con el dinero cuando cancela el estudiante: depende de cuándo.
     *
     * <p>La liberación tardía hay que hacerla aquí y no dejarla correr: una reserva cancelada nunca
     * llega a COMPLETED, así que el job de cierre no la va a mirar jamás. Sin esto el profesor
     * apartaría su hora, la perdería por un aviso de última hora y encima no cobraría — que es lo
     * contrario de lo que dicen los Términos.
     */
    private void resolverCancelacionDelEstudiante(Booking booking) {
        Duration ventana = Duration.ofHours(settings.getInt(STUDENT_WINDOW));
        boolean aTiempo = booking.getCancelledAt() == null
                || !booking.getStartsAt().minus(ventana).isBefore(booking.getCancelledAt());

        if (aTiempo) {
            payments.refundToCredit(booking.getId(), CreditReason.CANCELLED_BY_STUDENT,
                    booking.getCancelledBy());
        } else {
            payments.release(booking.getId());
        }
    }
}
