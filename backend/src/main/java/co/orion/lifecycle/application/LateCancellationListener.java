package co.orion.lifecycle.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.scheduling.domain.AbsenceKind;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCancelledEvent;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.ProfessorAbsence;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.ProfessorAbsenceRepository;
import co.orion.catalog.application.PlatformSettingsService;

/**
 * El profesor que cancela con la clase encima deja constancia.
 *
 * <p>Cancelar siempre se puede, y esa es la política: la ventana no bloquea, decide. Para el
 * estudiante decide su dinero; para el profesor, su historial. Sin este registro, avisar dos horas
 * antes salía gratis todas las veces, y el estudiante que se queda sin clase el mismo día no
 * distingue entre eso y una ausencia.
 *
 * <p>Cuenta para la misma escalera de sanciones que las ausencias confirmadas, con su propio
 * {@code kind} para que quien la revise sepa cuál de las dos faltas fue. Y como todas: se
 * <strong>propone</strong>, la aplica una persona (salvo que {@code sanctions_mode} diga otra cosa).
 *
 * <p>Va en {@code lifecycle} porque es el único módulo que puede mirar la reserva y la reputación a
 * la vez, y por evento AFTER_COMMIT porque un fallo aquí no puede deshacer una cancelación que el
 * estudiante ya vio confirmada.
 */
@Component
public class LateCancellationListener {

    private static final Logger log = LoggerFactory.getLogger(LateCancellationListener.class);
    private static final String PROFESSOR_WINDOW = "professor_cancel_hours";

    private final BookingRepository bookings;
    private final ProfessorAbsenceRepository absences;
    private final PlatformSettingsService settings;
    private final ApplicationEventPublisher publisher;

    public LateCancellationListener(BookingRepository bookings,
                                    ProfessorAbsenceRepository absences,
                                    PlatformSettingsService settings,
                                    ApplicationEventPublisher publisher) {
        this.bookings = bookings;
        this.absences = absences;
        this.settings = settings;
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCancelled(BookingCancelledEvent event) {
        try {
            Booking booking = bookings.findById(event.bookingId()).orElse(null);
            if (booking == null || booking.getStatus() != BookingStatus.CANCELLED_BY_PROFESSOR) {
                return;
            }
            if (!fueTardia(booking)) {
                return;
            }
            // Una clase produce como mucho una falta, venga de donde venga.
            if (absences.existsByBookingId(booking.getId())) {
                return;
            }

            UUID professorId = booking.getProfessorId();
            absences.save(new ProfessorAbsence(professorId, booking.getId(), null,
                    AbsenceKind.LATE_CANCELLATION, booking.getStartsAt()));
            log.info("Cancelación tardía del profesor {} sobre la clase {}",
                    professorId, booking.getId());

            publisher.publishEvent(new ProfessorAbsenceRecorded(professorId, booking.getId(),
                    AbsenceKind.LATE_CANCELLATION));
        } catch (RuntimeException ex) {
            log.error("No se pudo registrar la cancelación tardía de la reserva {}",
                    event.bookingId(), ex);
        }
    }

    /** Dentro de la ventana del profesor: la misma frontera que decide el dinero del estudiante. */
    private boolean fueTardia(Booking booking) {
        Instant cancelledAt = booking.getCancelledAt();
        if (cancelledAt == null) {
            return false;
        }
        Duration ventana = Duration.ofHours(settings.getInt(PROFESSOR_WINDOW));
        return booking.getStartsAt().minus(ventana).isBefore(cancelledAt);
    }
}
