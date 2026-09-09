package co.orion.billing.application;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.orion.shared.observability.JobRunRegistry;

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
    private final JobRunRegistry runs;
    private final Clock clock;

    public PaymentExpiryJob(BookingService bookings, PaymentLifecycleService payments,
                            JobRunRegistry runs, Clock clock) {
        this.bookings = bookings;
        this.payments = payments;
        this.runs = runs;
        this.clock = clock;
    }

    public static final String JOB_NAME = "payment-expiry";

    @Scheduled(fixedDelayString = "${orion.payments.expiry-job.interval-ms:300000}")
    public void expireOverduePayments() {
        try {
            List<Booking> overdue = bookings.findExpiredPendingPayments();
            if (!overdue.isEmpty()) {
                log.info("Expirando {} reserva(s) sin pagar", overdue.size());
                overdue.forEach(booking -> {
                    try {
                        payments.cancelUnpaid(booking.getId());
                    } catch (RuntimeException ex) {
                        log.error("No se pudo expirar la reserva {}", booking.getId(), ex);
                    }
                });
            }
            // Se registra SIEMPRE, también cuando no había nada que expirar: un job que corre y no
            // encuentra nada tiene que ser distinguible de uno que no corre, que es justo lo que
            // el vigilante necesita saber.
            runs.recordSuccess(JOB_NAME, clock.instant(), overdue.size() + " reserva(s) liberada(s)");
        } catch (RuntimeException ex) {
            runs.recordFailure(JOB_NAME, clock.instant(), ex.getMessage());
            throw ex;
        }
    }
}
