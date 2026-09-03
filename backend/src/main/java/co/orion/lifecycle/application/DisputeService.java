package co.orion.lifecycle.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.application.PaymentLifecycleService;
import co.orion.billing.domain.CreditReason;
import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.domain.User;
import co.orion.lifecycle.domain.Dispute;
import co.orion.lifecycle.domain.DisputeReason;
import co.orion.lifecycle.domain.DisputeStatus;
import co.orion.lifecycle.persistence.DisputeRepository;
import co.orion.scheduling.application.BookingService;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.ProfessorAbsence;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.ProfessorAbsenceRepository;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Reclamos: el cauce para "el profesor no se presentó".
 *
 * Este servicio vive en su propio módulo porque es el único punto del sistema que necesita ver las
 * tres cosas a la vez — la reserva, el pago y el historial del profesor — y ninguno de esos módulos
 * puede depender de los otros dos sin cerrar un ciclo.
 *
 * La ventana para reclamar tiene dos bordes y los dos importan: no se puede reclamar a los cinco
 * minutos (el profesor puede estar llegando) ni tres días después (nadie recuerda lo que pasó, y el
 * profesor ya contaba con ese dinero).
 */
@Service
public class DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);
    private static final String REPORT_AFTER_MINUTES = "no_show_report_minutes";
    private static final String REPORT_WINDOW_HOURS = "dispute_report_window_hours";

    private final DisputeRepository disputes;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final ProfessorAbsenceRepository absences;
    private final PaymentLifecycleService payments;
    private final PlatformSettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DisputeService(DisputeRepository disputes,
                          BookingRepository bookings,
                          BookingService bookingService,
                          ProfessorAbsenceRepository absences,
                          PaymentLifecycleService payments,
                          PlatformSettingsService settings,
                          ApplicationEventPublisher events,
                          Clock clock) {
        this.disputes = disputes;
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.absences = absences;
        this.payments = payments;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    /**
     * El estudiante reporta un problema. La clase pasa a UNDER_REVIEW y su pago a DISPUTED: a
     * partir de aquí el autocompletado no la toca y el dinero se queda quieto hasta que alguien
     * decida.
     */
    @Transactional
    public Dispute report(User student, UUID bookingId, String reasonName, String description) {
        Booking booking = bookings.findById(bookingId)
                .filter(candidate -> candidate.getStudentId().equals(student.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        if (!booking.isConfirmed()) {
            throw new ConflictException(
                    "Solo una clase confirmada admite un reclamo (esta está en " + booking.getStatus() + ")");
        }

        Instant now = clock.instant();
        requireWithinReportWindow(booking, now);

        DisputeReason reason = parseReason(reasonName);
        booking.putUnderReview();
        bookings.save(booking);

        Dispute dispute;
        try {
            dispute = disputes.saveAndFlush(
                    new Dispute(bookingId, student.getId(), reason, description));
        } catch (DataIntegrityViolationException ex) {
            // El índice único parcial: un solo reclamo vivo por clase.
            throw new ConflictException("Esta clase ya tiene un reclamo abierto");
        }

        payments.markDisputed(bookingId);
        events.publishEvent(new DisputeOpened(dispute.getId()));
        return dispute;
    }

    /**
     * El admin cierra el reclamo y con él se mueve el dinero:
     *
     * <ul>
     *   <li><b>A favor del estudiante</b> — la clase no ocurrió por causa del profesor. Se le
     *       devuelve el valor completo como saldo, la reserva queda NO_SHOW_PROFESSOR y se registra
     *       la ausencia, que es lo que después evalúan las sanciones.</li>
     *   <li><b>A favor del profesor o desestimado</b> — la clase contó. La reserva queda COMPLETED
     *       y el pago se libera, igual que si el autocompletado la hubiera cerrado.</li>
     * </ul>
     */
    @Transactional
    public Dispute resolve(User admin, UUID disputeId, String outcomeName, String note) {
        Dispute dispute = disputes.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Reclamo no encontrado"));
        if (!dispute.isOpen()) {
            throw new ConflictException("Este reclamo ya se resolvió");
        }

        DisputeStatus outcome = parseOutcome(outcomeName);
        Instant now = clock.instant();

        Booking booking = bookingService.require(dispute.getBookingId());
        boolean lessonHeld = !outcome.favoursStudent();
        booking.resolveReview(lessonHeld, now);
        bookings.save(booking);

        if (lessonHeld) {
            payments.releaseDisputed(booking.getId());
        } else {
            payments.refundDisputed(booking.getId(), CreditReason.DISPUTE_RESOLVED, admin.getId());
            recordAbsence(booking, dispute, now);
        }

        try {
            dispute.resolve(outcome, note, admin.getId(), now);
        } catch (IllegalArgumentException ex) {
            throw new UnprocessableException(ex.getMessage());
        }
        Dispute resolved = disputes.save(dispute);
        events.publishEvent(new DisputeResolved(resolved.getId(), booking.getProfessorId(), !lessonHeld));
        return resolved;
    }

    @Transactional
    public Dispute take(User admin, UUID disputeId) {
        Dispute dispute = disputes.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Reclamo no encontrado"));
        try {
            dispute.takeForReview();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        return disputes.save(dispute);
    }

    @Transactional(readOnly = true)
    public List<Dispute> search(String statusName) {
        if (statusName == null || statusName.isBlank()) {
            return disputes.findAllByOrderByCreatedAtDesc();
        }
        if ("OPEN".equalsIgnoreCase(statusName.trim())) {
            // "Abiertos" para quien mira son los dos que todavía esperan decisión.
            return disputes.findByStatusInOrderByCreatedAtDesc(
                    List.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW));
        }
        return disputes.findByStatusOrderByCreatedAtDesc(parseAnyStatus(statusName));
    }

    @Transactional(readOnly = true)
    public List<Dispute> ofBooking(UUID bookingId) {
        return disputes.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    /** Solo el estudiante de la clase puede reclamarla; el profesor tiene la mensajería. */
    void requireStudent(User actor, Booking booking) {
        if (!booking.getStudentId().equals(actor.getId())) {
            throw new ForbiddenException("Solo el estudiante de la clase puede reportar un problema");
        }
    }

    private void recordAbsence(Booking booking, Dispute dispute, Instant now) {
        if (absences.existsByBookingId(booking.getId())) {
            return;   // una clase produce como mucho una ausencia
        }
        absences.save(new ProfessorAbsence(
                booking.getProfessorId(), booking.getId(), dispute.getId(), booking.getStartsAt()));
        log.info("Ausencia registrada del profesor {} por la clase {}",
                booking.getProfessorId(), booking.getId());
    }

    /**
     * Ni demasiado pronto ni demasiado tarde. El borde inferior le da al profesor el margen de
     * llegar; el superior evita que se reclame una clase que el profesor ya dio por cobrada.
     */
    private void requireWithinReportWindow(Booking booking, Instant now) {
        Duration grace = Duration.ofMinutes(settings.getInt(REPORT_AFTER_MINUTES));
        Instant opensAt = booking.getStartsAt().plus(grace);
        Instant closesAt = booking.getEndsAt().plus(Duration.ofHours(settings.getInt(REPORT_WINDOW_HOURS)));

        if (now.isBefore(opensAt)) {
            throw new UnprocessableException(
                    "Espera al menos " + grace.toMinutes() + " minutos desde la hora de inicio antes de reportar");
        }
        if (now.isAfter(closesAt)) {
            throw new UnprocessableException(
                    "El plazo para reportar un problema con esta clase ya venció");
        }
    }

    private DisputeReason parseReason(String name) {
        try {
            return DisputeReason.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new UnprocessableException("Motivo de reclamo desconocido: " + name);
        }
    }

    private DisputeStatus parseOutcome(String name) {
        DisputeStatus outcome = parseAnyStatus(name);
        if (outcome.isOpen()) {
            throw new UnprocessableException("La resolución tiene que cerrar el reclamo");
        }
        return outcome;
    }

    private DisputeStatus parseAnyStatus(String name) {
        try {
            return DisputeStatus.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new UnprocessableException("Estado de reclamo desconocido: " + name);
        }
    }
}
