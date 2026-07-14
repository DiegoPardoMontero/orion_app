package co.orion.scheduling.application;

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
import co.orion.scheduling.domain.BookingCreatedEvent;
import co.orion.scheduling.domain.BookingModality;
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

    public BookingService(BookingRepository bookings,
                          UserRepository users,
                          SlotQueryService slots,
                          ApplicationEventPublisher events) {
        this.bookings = bookings;
        this.users = users;
        this.slots = slots;
        this.events = events;
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
