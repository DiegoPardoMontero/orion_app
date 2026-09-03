package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.domain.RescheduleRequest;
import co.orion.scheduling.domain.RescheduleRequested;
import co.orion.scheduling.domain.RescheduleResolved;
import co.orion.scheduling.domain.RescheduleStatus;
import co.orion.scheduling.domain.Slot;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.RescheduleRequestRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

import org.springframework.context.ApplicationEventPublisher;

/**
 * Reprogramar es negociar, no imponer. Quien propone escoge un cupo REAL de la agenda del profesor;
 * la contraparte acepta (y ahí sí se mueve la reserva) o propone otro horario.
 *
 * El pago no se toca en ninguna de las dos ramas: es la misma clase a otra hora, y su precio se
 * congeló al reservar.
 */
@Service
public class RescheduleRequestService {

    private static final Logger log = LoggerFactory.getLogger(RescheduleRequestService.class);
    private static final String MIN_HOURS = "reschedule_min_hours";

    private final RescheduleRequestRepository requests;
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final SlotQueryService slots;
    private final PlatformSettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RescheduleRequestService(RescheduleRequestRepository requests,
                                    BookingRepository bookings,
                                    BookingService bookingService,
                                    SlotQueryService slots,
                                    PlatformSettingsService settings,
                                    ApplicationEventPublisher events,
                                    Clock clock) {
        this.requests = requests;
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.slots = slots;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Propone mover la clase. Lo puede hacer cualquiera de los dos, incluso dentro de la ventana de
     * cancelación: es justo la salida que se le ofrece al profesor que ya no puede cancelar.
     */
    @Transactional
    public RescheduleRequest propose(User actor, UUID bookingId, Instant proposedStartsAt, String reason) {
        Booking booking = requireParticipant(actor, bookingId);

        if (!booking.isConfirmed()) {
            throw new ConflictException("Solo una clase confirmada se puede reprogramar");
        }

        Instant now = clock.instant();
        Duration margin = Duration.ofHours(settings.getInt(MIN_HOURS));
        if (proposedStartsAt.isBefore(now.plus(margin))) {
            throw new UnprocessableException(
                    "Propón un horario con al menos " + margin.toHours() + " horas de anticipación");
        }
        if (proposedStartsAt.equals(booking.getStartsAt())) {
            throw new BusinessRuleViolationException("Propón un horario distinto al actual");
        }

        // El cupo tiene que existir de verdad en la agenda del profesor. Proponer una hora en la
        // que no atiende es hacerle perder el tiempo a la contraparte.
        requireProfessorOffersSlot(booking.getProfessorId(), proposedStartsAt);

        RescheduleRequest request = new RescheduleRequest(bookingId, actor.getId(),
                proposedStartsAt, proposedStartsAt.plus(Duration.ofHours(1)), reason);
        try {
            RescheduleRequest saved = requests.saveAndFlush(request);
            events.publishEvent(new RescheduleRequested(saved.getId()));
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // El índice único parcial: una sola propuesta viva por reserva. Dos abiertas a la vez
            // son una negociación que nadie sabe cerrar.
            throw new ConflictException("Esta clase ya tiene una propuesta de cambio sin responder");
        }
    }

    /**
     * Acepta y mueve la clase. Solo la contraparte: aceptar tu propia propuesta sería reprogramar
     * unilateralmente por la puerta de atrás.
     */
    @Transactional
    public Booking accept(User actor, UUID requestId) {
        RescheduleRequest request = requireResponder(actor, requestId);

        // El cupo pudo volar entre la propuesta y la aceptación. moveTo revalida contra la agenda
        // real y el índice único arbitra; aquí solo se traduce a un mensaje que se entiende.
        Booking moved;
        try {
            moved = bookingService.moveTo(request.getBookingId(), request.getProposedStartsAt());
        } catch (UnprocessableException | ConflictException ex) {
            throw new ConflictException(
                    "Ese horario ya no está libre. Pídele a la otra persona que proponga otro.");
        }

        request.accept(clock.instant());
        requests.save(request);
        events.publishEvent(new RescheduleResolved(request.getId(), true));
        return moved;
    }

    /** Rechaza. La clase sigue a su hora original; quien rechaza puede proponer otra cosa. */
    @Transactional
    public RescheduleRequest decline(User actor, UUID requestId) {
        RescheduleRequest request = requireResponder(actor, requestId);
        request.decline(clock.instant());
        RescheduleRequest saved = requests.save(request);
        events.publishEvent(new RescheduleResolved(saved.getId(), false));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<RescheduleRequest> pendingFor(UUID userId) {
        return requests.findPendingOf(userId);
    }

    @Transactional(readOnly = true)
    public List<RescheduleRequest> ofBooking(UUID bookingId) {
        return requests.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    /**
     * Cierra las propuestas cuya clase original ya empezó. Sin esto quedarían PENDING para siempre,
     * bloqueando el índice único y ofreciendo "aceptar" un horario que ya pasó.
     */
    @Transactional
    public int expireOverdue() {
        List<RescheduleRequest> overdue = requests.findOverdue(clock.instant());
        overdue.forEach(request -> request.expire(clock.instant()));
        requests.saveAll(overdue);
        if (!overdue.isEmpty()) {
            log.info("Vencidas {} propuesta(s) de reprogramación sin responder", overdue.size());
        }
        return overdue.size();
    }

    private void requireProfessorOffersSlot(UUID professorId, Instant startsAt) {
        LocalDate date = startsAt.atZone(BusinessZone.BOGOTA).toLocalDate();
        boolean offered = slots.availableSlots(professorId, date, date).stream()
                .map(Slot::startsAt)
                .anyMatch(slot -> slot.toInstant().equals(startsAt));
        if (!offered) {
            throw new UnprocessableException("Ese horario no está disponible en la agenda del profesor");
        }
    }

    private Booking requireParticipant(User actor, UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        boolean mine = booking.getStudentId().equals(actor.getId())
                || booking.getProfessorId().equals(actor.getId())
                || actor.getRole() == UserRole.ADMIN;
        if (!mine) {
            throw new ResourceNotFoundException("Reserva no encontrada");
        }
        return booking;
    }

    private RescheduleRequest requireResponder(User actor, UUID requestId) {
        RescheduleRequest request = requests.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Propuesta no encontrada"));
        requireParticipant(actor, request.getBookingId());

        if (!request.isPending()) {
            throw new ConflictException("Esta propuesta ya se resolvió");
        }
        if (!request.awaitsResponseFrom(actor.getId()) && actor.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Esta propuesta la tiene que responder la otra persona");
        }
        return request;
    }

    /** Estado por si algún día se consulta una propuesta concreta. */
    @Transactional(readOnly = true)
    public RescheduleRequest openRequestOf(UUID bookingId) {
        return requests.findByBookingIdAndStatus(bookingId, RescheduleStatus.PENDING).orElse(null);
    }
}
