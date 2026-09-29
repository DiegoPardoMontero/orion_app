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

import co.orion.billing.domain.Payment;
import co.orion.billing.persistence.PaymentRepository;
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
    private static final String PROFESSOR_GRACE = "professor_cancel_grace_minutes";

    private final BookingRepository bookings;
    private final ProfessorAbsenceRepository absences;
    private final PaymentRepository payments;
    private final PlatformSettingsService settings;
    private final ApplicationEventPublisher publisher;

    public LateCancellationListener(BookingRepository bookings,
                                    ProfessorAbsenceRepository absences,
                                    PaymentRepository payments,
                                    PlatformSettingsService settings,
                                    ApplicationEventPublisher publisher) {
        this.bookings = bookings;
        this.absences = absences;
        this.payments = payments;
        this.settings = settings;
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCancelled(BookingCancelledEvent event) {
        // Una reserva sin pagar no era una clase: al profesor ni se le anunció, así que soltarla no
        // puede costarle nada.
        if (event.wasAwaitingPayment()) {
            return;
        }
        try {
            Booking booking = bookings.findById(event.bookingId()).orElse(null);
            if (booking == null || booking.getStatus() != BookingStatus.CANCELLED_BY_PROFESSOR) {
                return;
            }
            // El ensayo del admin no es trabajo del profesor: ni suma ni resta en su historial.
            if (booking.isRehearsal()) {
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

    /**
     * Tardía si llegó después de las dos fronteras a la vez: la ventana del profesor antes de la
     * clase (la misma que decide el dinero del estudiante) y la gracia desde que se hizo la reserva.
     *
     * <p>La segunda existe porque se puede reservar con menos antelación que la ventana: a quien le
     * reservan una clase 8 horas antes con una ventana de 12, la ventana ya empezó cerrada y nunca
     * tuvo un momento para cancelar sin falta. Con la gracia, lo tiene durante un rato después de
     * enterarse. Si la reserva se hizo con margen, la gracia vence mucho antes que la ventana y no
     * cambia nada.
     *
     * <p>El «enterarse» es cuando la reserva quedó pagada, no cuando se creó: al profe se le anuncia
     * la clase al confirmarse, y el cobro puede tardar hasta {@code payment_hold_minutes} (hoy 20).
     * Contada desde la creación, la gracia de 60 minutos le dejaba 40. Una reserva sin cobro (la
     * clase de prueba, gratis) se confirma al crearse, y ahí las dos horas coinciden.
     */
    private boolean fueTardia(Booking booking) {
        Instant cancelledAt = booking.getCancelledAt();
        if (cancelledAt == null) {
            return false;
        }
        Instant ventanaAbre = booking.getStartsAt()
                .minus(Duration.ofHours(settings.getInt(PROFESSOR_WINDOW)));
        Instant seEntero = payments.findByBookingId(booking.getId())
                .map(Payment::getPaidAt)
                .orElse(booking.getCreatedAt());
        Instant graciaVence = seEntero.plus(Duration.ofMinutes(settings.getInt(PROFESSOR_GRACE)));
        Instant frontera = ventanaAbre.isAfter(graciaVence) ? ventanaAbre : graciaVence;
        return cancelledAt.isAfter(frontera);
    }
}
