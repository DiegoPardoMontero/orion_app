package co.orion.lifecycle.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.application.PaymentLifecycleService;
import co.orion.catalog.application.PlatformSettingsService;
import co.orion.lifecycle.persistence.DisputeRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;

/**
 * El corazón económico del sistema: cierra las clases que ya pasaron y nadie reclamó, y con ese
 * cierre le libera el dinero al profesor.
 *
 * Si este job se cae, NADIE COBRA. No hay error, no hay alarma: simplemente los pagos se quedan
 * retenidos para siempre. Por eso deja su última corrida registrada y visible en el panel de admin
 * — un job silencioso que no corre es indistinguible de uno que corre y no encuentra nada.
 *
 * Es idempotente por {@code completed_at}: puede correr dos veces sobre la misma clase sin liberar
 * el pago dos veces.
 */
@Component
public class LessonAutoCompleteJob {

    private static final Logger log = LoggerFactory.getLogger(LessonAutoCompleteJob.class);
    private static final String GRACE_HOURS = "auto_complete_hours";

    private final BookingRepository bookings;
    private final DisputeRepository disputes;
    private final PaymentLifecycleService payments;
    private final PlatformSettingsService settings;
    private final JobRunRegistry runs;
    private final Clock clock;

    public LessonAutoCompleteJob(BookingRepository bookings,
                                 DisputeRepository disputes,
                                 PaymentLifecycleService payments,
                                 PlatformSettingsService settings,
                                 JobRunRegistry runs,
                                 Clock clock) {
        this.bookings = bookings;
        this.disputes = disputes;
        this.payments = payments;
        this.settings = settings;
        this.runs = runs;
        this.clock = clock;
    }

    public static final String JOB_NAME = "lesson-auto-complete";

    @Scheduled(fixedDelayString = "${orion.jobs.auto-complete.interval-ms:3600000}")
    public void closeFinishedLessons() {
        try {
            int closed = run();
            runs.recordSuccess(JOB_NAME, clock.instant(), closed + " clase(s) cerrada(s)");
        } catch (RuntimeException ex) {
            log.error("El autocompletado de clases falló", ex);
            runs.recordFailure(JOB_NAME, clock.instant(), ex.getMessage());
        }
    }

    /**
     * Separado del método programado para poder invocarlo desde un test sin esperar al reloj.
     * Cada clase se cierra en su propia transacción: un fallo en una no puede impedir que cobren
     * los demás profesores.
     */
    public int run() {
        Instant now = clock.instant();
        Instant deadline = now.minus(Duration.ofHours(settings.getInt(GRACE_HOURS)));

        List<Booking> candidates = bookings.findByStatusAndEndsAtLessThanAndCompletedAtIsNull(
                BookingStatus.CONFIRMED, deadline);
        if (candidates.isEmpty()) {
            return 0;
        }

        // Un reclamo abierto congela la clase: se resuelve a mano, no por vencimiento.
        Set<UUID> disputed = Set.copyOf(disputes.findBookingIdsWithOpenDispute());

        int closed = 0;
        for (Booking booking : candidates) {
            if (disputed.contains(booking.getId())) {
                continue;
            }
            try {
                if (close(booking.getId(), now)) {
                    closed++;
                }
            } catch (RuntimeException ex) {
                log.error("No se pudo cerrar la clase {}", booking.getId(), ex);
            }
        }
        if (closed > 0) {
            log.info("Autocompletado: {} clase(s) cerradas y liberadas", closed);
        }
        return closed;
    }

    @Transactional
    protected boolean close(UUID bookingId, Instant now) {
        Booking booking = bookings.findById(bookingId).orElse(null);
        if (booking == null || !booking.autoComplete(now)) {
            return false;   // ya estaba cerrada: el job puede correr dos veces sin duplicar nada
        }
        bookings.save(booking);
        payments.release(bookingId);
        return true;
    }
}
