package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCancelledEvent;
import co.orion.scheduling.domain.BookingCreatedEvent;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.domain.Slot;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

@Service
public class BookingService {

    private static final Duration CLASS_LENGTH = Duration.ofHours(1);

    private final BookingRepository bookings;
    private final UserRepository users;
    private final SlotQueryService slots;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingService(BookingRepository bookings,
                          UserRepository users,
                          SlotQueryService slots,
                          ApplicationEventPublisher events,
                          Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.slots = slots;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Booking create(User actor,
                          UUID professorId,
                          Instant startsAt,
                          String modalityName,
                          String locationNote,
                          UUID requestedStudentId) {
        UUID studentId = resolveStudent(actor, requestedStudentId);
        BookingModality modality = parseModality(modalityName);
        Instant endsAt = startsAt.plus(CLASS_LENGTH);

        // El profesor debe existir y estar publicado: si no, availableSlots lanza 404.
        requireSlotIsAvailable(professorId, startsAt);

        if (bookings.studentHasOverlappingBooking(studentId, startsAt, endsAt)) {
            throw new UnprocessableException("El estudiante ya tiene una clase confirmada a esa hora");
        }

        Booking booking = new Booking(
                studentId, professorId, startsAt, endsAt, modality, locationNote, actor.getId());

        Booking saved = saveOrLoseTheRace(booking);
        events.publishEvent(new BookingCreatedEvent(saved.getId()));
        return saved;
    }

    /**
     * Cancela una reserva. Quién puede y bajo qué condiciones depende del rol:
     * el estudiante y el profesor solo las suyas y con 24 h de margen; el admin, cualquiera
     * confirmada y a cualquier hora — es la válvula de "fuerza mayor" del manual corporativo.
     */
    @Transactional
    public Booking cancel(User actor, UUID bookingId, String reason) {
        Booking booking = bookings.findById(bookingId)
                .filter(candidate -> canSee(actor, candidate))
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        if (!booking.isConfirmed()) {
            throw new ConflictException("La reserva ya no está confirmada");
        }

        Instant now = clock.instant();
        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        if (!isAdmin && !booking.isCancellableAt(now)) {
            throw new UnprocessableException(
                    "Con menos de 24 horas de anticipación la clase se considera impartida (política Orión)");
        }

        booking.cancel(cancellationStatusFor(actor), actor.getId(), now, reason);
        Booking cancelled = bookings.save(booking);
        events.publishEvent(new BookingCancelledEvent(cancelled.getId()));
        return cancelled;
    }

    /**
     * Reprograma una reserva a otro cupo del MISMO profesor. Mismas reglas de quién y cuándo que la
     * cancelación (el dueño con 24 h de margen; el admin sin límite): reprogramar dentro de la
     * ventana equivaldría a cancelar dentro de ella. El nuevo cupo se valida como en una reserva
     * nueva y la constraint sigue siendo el árbitro de la carrera. Emite `BookingCreatedEvent` para
     * que salga una confirmación con el nuevo horario y su invitación de calendario.
     */
    @Transactional
    public Booking reschedule(User actor, UUID bookingId, Instant newStartsAt) {
        Booking booking = bookings.findById(bookingId)
                .filter(candidate -> canSee(actor, candidate))
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        if (!booking.isConfirmed()) {
            throw new ConflictException("La reserva ya no está confirmada");
        }

        Instant now = clock.instant();
        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        if (!isAdmin && !booking.isCancellableAt(now)) {
            throw new UnprocessableException(
                    "Con menos de 24 horas de anticipación la clase se considera impartida (política Orión)");
        }

        if (newStartsAt.equals(booking.getStartsAt())) {
            throw new BusinessRuleViolationException("Elige un horario distinto al actual");
        }

        Instant newEndsAt = newStartsAt.plus(CLASS_LENGTH);
        requireSlotIsAvailable(booking.getProfessorId(), newStartsAt);

        // El cupo nuevo no solapa el viejo (cupos alineados a la hora), así que la reserva actual
        // no cuenta; sí cuenta cualquier OTRA clase confirmada del estudiante a esa hora.
        if (bookings.studentHasOverlappingBooking(booking.getStudentId(), newStartsAt, newEndsAt)) {
            throw new UnprocessableException("El estudiante ya tiene una clase confirmada a esa hora");
        }

        booking.reschedule(newStartsAt, newEndsAt);
        Booking saved = saveOrLoseTheRace(booking);
        events.publishEvent(new BookingCreatedEvent(saved.getId()));
        return saved;
    }

    /** Una reserva ajena responde 404, no 403: no confirmamos que exista. El admin lo ve todo. */
    private boolean canSee(User actor, Booking booking) {
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case STUDENT -> booking.getStudentId().equals(actor.getId());
            case PROFESSOR -> booking.getProfessorId().equals(actor.getId());
        };
    }

    private BookingStatus cancellationStatusFor(User actor) {
        return switch (actor.getRole()) {
            case STUDENT -> BookingStatus.CANCELLED_BY_STUDENT;
            case PROFESSOR -> BookingStatus.CANCELLED_BY_PROFESSOR;
            case ADMIN -> BookingStatus.CANCELLED_BY_ADMIN;
        };
    }

    /**
     * Chequeo amable + constraint como árbitro final. requireSlotIsAvailable ya rechazó los cupos
     * ocupados con un 422 claro, pero entre ese chequeo y este INSERT hay una ventana en la que
     * otra petición puede colarse: ningún if de Java la cierra, solo la base, que serializa las
     * escrituras. El índice único parcial es quien decide, y quien pierde recibe un 409.
     */
    private Booking saveOrLoseTheRace(Booking booking) {
        try {
            return bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Alguien acaba de tomar este cupo");
        }
    }

    /** Un STUDENT solo reserva para sí mismo; un ADMIN reserva en nombre de otro; un PROFESSOR no reserva. */
    private UUID resolveStudent(User actor, UUID requestedStudentId) {
        if (actor.getRole() == UserRole.ADMIN) {
            if (requestedStudentId == null) {
                throw new BusinessRuleViolationException("Un admin debe indicar el studentId de la reserva");
            }
            User student = users.findById(requestedStudentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Estudiante no encontrado"));
            if (student.getRole() != UserRole.STUDENT || !student.isActive()) {
                throw new BusinessRuleViolationException("El studentId debe ser un estudiante activo");
            }
            return student.getId();
        }

        if (requestedStudentId != null && !requestedStudentId.equals(actor.getId())) {
            throw new ForbiddenException("No puedes reservar en nombre de otro estudiante");
        }
        return actor.getId();
    }

    private BookingModality parseModality(String modalityName) {
        try {
            return BookingModality.valueOf(modalityName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("modality debe ser VIRTUAL o IN_PERSON");
        }
    }

    /**
     * Una sola fuente de verdad: el cupo se valida contra el MISMO servicio que alimenta el
     * endpoint público de cupos. Si un día cambia la política de disponibilidad, cambia en un sitio.
     */
    private void requireSlotIsAvailable(UUID professorId, Instant startsAt) {
        LocalDate date = startsAt.atZone(BusinessZone.BOGOTA).toLocalDate();
        boolean available = slots.availableSlots(professorId, date, date).stream()
                .map(Slot::startsAt)
                .anyMatch(slotStart -> slotStart.toInstant().equals(startsAt));

        if (!available) {
            throw new UnprocessableException("El cupo no está disponible");
        }
    }
}
