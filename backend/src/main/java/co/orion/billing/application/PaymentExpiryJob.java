package co.orion.billing.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.orion.scheduling.application.BookingService;
import co.orion.scheduling.domain.Booking;

/**
 * Libera los cupos de las reservas que nadie pagó a tiempo. Es la tercera salida del flujo, junto
 * al webhook aprobado y al rechazado: la pasarela a veces sencillamente no responde —el estudiante
 * cierra la pestaña de PSE y ya— y sin este barrido ese cupo quedaría bloqueado para siempre.
 *
 * Cada reserva se procesa por separado a propósito: un fallo en una no puede impedir que se liberen
 * las demás.
 */
@Component
public class PaymentExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryJob.class);

    private final BookingService bookings;
    private final PaymentLifecycleService payments;

    public PaymentExpiryJob(BookingService bookings, PaymentLifecycleService payments) {
        this.bookings = bookings;
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${orion.payments.expiry-job.interval-ms:300000}")
    public void expireOverduePayments() {
        List<Booking> overdue = bookings.findExpiredPendingPayments();
        if (overdue.isEmpty()) {
            return;
        }
        log.info("Expirando {} reserva(s) sin pagar", overdue.size());
        overdue.forEach(booking -> {
            try {
                payments.cancelUnpaid(booking.getId());
            } catch (RuntimeException ex) {
                log.error("No se pudo expirar la reserva {}", booking.getId(), ex);
            }
        });
    }
}
