package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.persistence.ProfessorLanguageRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCancelledEvent;
import co.orion.scheduling.domain.BookingCreatedEvent;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.Slot;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.time.BusinessZone;

@Service
public class BookingService {

    private static final Duration CLASS_LENGTH = Duration.ofHours(1);
    private static final String STUDENT_WINDOW = "student_cancel_hours";
    private static final String PROFESSOR_WINDOW = "professor_cancel_hours";

    private final BookingRepository bookings;
    private final UserRepository users;
    private final SlotQueryService slots;
    private final ProfessorLanguageRepository professorLanguages;
    private final MeetingLinkProvider meetingLinks;
    private final PaymentInitiator payments;
    private final PlatformSettingsService settings;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingService(BookingRepository bookings,
                          UserRepository users,
                          SlotQueryService slots,
                          ProfessorLanguageRepository professorLanguages,
                          MeetingLinkProvider meetingLinks,
                          PaymentInitiator payments,
                          PlatformSettingsService settings,
                          ApplicationEventPublisher events,
                          Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.slots = slots;
        this.professorLanguages = professorLanguages;
        this.meetingLinks = meetingLinks;
        this.payments = payments;
        this.settings = settings;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Cuánta anticipación necesita cada actor para cancelar. Se lee de {@code platform_settings} en
     * CADA evaluación, nunca se cachea: cambiar la política tiene que ser un UPDATE, no un despliegue.
     * El admin no tiene ventana — es la válvula de fuerza mayor.
     */
    public Duration cancellationWindowFor(UserRole role) {
        return switch (role) {
            case STUDENT -> Duration.ofHours(settings.getInt(STUDENT_WINDOW));
            case PROFESSOR -> Duration.ofHours(settings.getInt(PROFESSOR_WINDOW));
            case ADMIN -> Duration.ZERO;
        };
    }

    /** Una reserva recién creada y lo que hay que pagar por ella. */
    public record NewBooking(Booking booking, PaymentTicket ticket) {
    }

    /**
     * Crea la reserva en PENDING_PAYMENT: el cupo queda bloqueado por el índice único mientras el
     * estudiante paga, y vence solo si no paga a tiempo. Nada de correos de confirmación todavía
     * — una reserva sin pagar no es una clase, y anunciarla sería mentirle a los dos lados.
     *
     * Si el crédito del estudiante cubre la clase entera no hay pasarela que esperar y la reserva
     * se confirma aquí mismo.
     */
    @Transactional
    public NewBooking create(User actor,
                             UUID professorId,
                             Instant startsAt,
                             String modalityName,
                             String locationNote,
                             String requestedLanguage,
                             UUID requestedStudentId) {
        UUID studentId = resolveStudent(actor, requestedStudentId);
        BookingModality modality = parseModality(modalityName);
        Instant endsAt = startsAt.plus(CLASS_LENGTH);

        // El profesor debe existir y estar publicado: si no, availableSlots lanza 404.
        requireSlotIsAvailable(professorId, startsAt);

        if (bookings.studentHasOverlappingBooking(studentId, startsAt, endsAt)) {
            throw new UnprocessableException("El estudiante ya tiene una clase reservada a esa hora");
        }

        Booking booking = new Booking(studentId, professorId, startsAt, endsAt, modality,
                locationNote, resolveLanguage(professorId, requestedLanguage), actor.getId(),
                payments.holdExpiry(clock.instant()));

        Booking saved = saveOrLoseTheRace(booking);
        PaymentTicket ticket = payments.initiate(saved);

        if (ticket.nothingToCharge()) {
            saved = confirm(saved);
        }
        return new NewBooking(saved, ticket);
    }

    /**
     * En qué idioma se da esta clase.
     *
     * Si el profesor enseña uno solo, se asigna sin preguntar: obligar a elegir entre una opción
     * es un paso de más. Si enseña varios, hay que decirlo — deducirlo sería inventarlo, y este es
     * justamente el dato que no se puede recuperar después.
     *
     * El idioma que llegue tiene que ser uno de los suyos. Que el frontend solo ofrezca los
     * correctos es cortesía; la comprobación es esto.
     */
    private String resolveLanguage(UUID professorId, String requested) {
        List<String> suyos = professorLanguages.findByProfessorId(professorId).stream()
                .map(pl -> pl.getLanguageCode())
                .toList();

        if (requested == null || requested.isBlank()) {
            if (suyos.size() == 1) {
                return suyos.get(0);
            }
            if (suyos.isEmpty()) {
                // Un profesor publicado sin idiomas no debería existir, pero si existe no se le
                // puede inventar uno: la reserva queda sin idioma, como las anteriores a la V20.
                return null;
            }
            throw new UnprocessableException(
                    "Este profesor enseña varios idiomas: elige en cuál quieres la clase.");
        }

        String normalizado = requested.trim().toUpperCase();
        if (!suyos.contains(normalizado)) {
            throw new UnprocessableException("Este profesor no enseña ese idioma.");
        }
        return normalizado;
    }

    /**
     * El pago entró: la reserva pasa a CONFIRMED y recién ahí salen la confirmación, el .ics y el
     * enlace de la sala. Lo llama billing cuando la pasarela aprueba (o cuando el crédito cubrió
     * todo). Idempotente frente a un webhook reenviado: si ya está confirmada, no hace nada.
     */
    @Transactional
    public Booking confirmPaid(UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        if (booking.isConfirmed()) {
            return booking;
        }
        if (!booking.isAwaitingPayment()) {
            throw new ConflictException("La reserva ya no admite confirmación de pago");
        }
        return confirm(booking);
    }

    /**
     * Se acabó el plazo para pagar (o la pasarela rechazó): el cupo vuelve al mercado. Igual que
     * la confirmación, es idempotente — el job puede pasar dos veces por la misma reserva.
     */
    @Transactional
    public void expirePendingPayment(UUID bookingId) {
        bookings.findById(bookingId)
                .filter(Booking::isAwaitingPayment)
                .ifPresent(booking -> {
                    booking.expire();
                    bookings.save(booking);
                });
    }

    /** Las reservas cuyo plazo de pago ya venció. La entrada del job de expiración. */
    @Transactional(readOnly = true)
    public List<Booking> findExpiredPendingPayments() {
        return bookings.findByStatusAndExpiresAtLessThanEqual(
                BookingStatus.PENDING_PAYMENT, clock.instant());
    }

    private Booking confirm(Booking booking) {
        booking.confirmPayment();
        // La sala virtual se genera con el id ya asignado por la base y se guarda antes del evento,
        // para que la confirmación (correo + .ics) salga ya con el link.
        if (booking.getModality() == BookingModality.VIRTUAL && booking.getMeetingLink() == null) {
            booking.assignMeetingLink(meetingLinks.linkFor(booking.getId()));
        }
        Booking confirmed = bookings.save(booking);
        events.publishEvent(new BookingCreatedEvent(confirmed.getId()));
        return confirmed;
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

        if (booking.getStatus().isTerminal()) {
            throw new ConflictException("La reserva ya no está activa");
        }

        Instant now = clock.instant();
        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        Duration window = cancellationWindowFor(actor.getRole());

        // La ventana de anticipación protege una clase que ya existe. Una reserva sin pagar todavía
        // no lo es: abandonar el checkout se puede hacer siempre, y billing devuelve el crédito.
        if (booking.isConfirmed() && !isAdmin && !booking.isCancellableAt(now, window)) {
            throw new UnprocessableException(lateCancellationMessage(actor.getRole(), window));
        }

        booking.cancel(cancellationStatusFor(actor), actor.getId(), now, reason);
        Booking cancelled = bookings.save(booking);
        events.publishEvent(new BookingCancelledEvent(cancelled.getId()));
        return cancelled;
    }

    /**
     * Mueve una reserva CONFIRMED a otro cupo del mismo profesor. Ya no es una acción directa de
     * nadie: la dispara {@code RescheduleRequestService} cuando la contraparte ACEPTA una propuesta.
     *
     * Antes el estudiante movía la clase solo, sin que el profesor se enterara hasta ver su agenda
     * cambiada. Una clase es un acuerdo entre dos: moverla la tiene que aceptar el otro.
     */
    @Transactional
    public Booking moveTo(UUID bookingId, Instant newStartsAt) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        if (!booking.isConfirmed()) {
            throw new ConflictException("La reserva ya no está confirmada");
        }
        if (newStartsAt.equals(booking.getStartsAt())) {
            throw new BusinessRuleViolationException("Elige un horario distinto al actual");
        }

        Instant newEndsAt = newStartsAt.plus(CLASS_LENGTH);
        requireSlotIsAvailable(booking.getProfessorId(), newStartsAt);

        // El cupo nuevo no solapa el viejo (cupos alineados a la hora), así que la reserva actual
        // no cuenta; sí cuenta cualquier OTRA clase del estudiante a esa hora.
        if (bookings.studentHasOverlappingBooking(booking.getStudentId(), newStartsAt, newEndsAt)) {
            throw new UnprocessableException("El estudiante ya tiene una clase reservada a esa hora");
        }

        booking.reschedule(newStartsAt, newEndsAt);
        Booking saved = saveOrLoseTheRace(booking);
        events.publishEvent(new BookingCreatedEvent(saved.getId()));
        return saved;
    }

    /** Lectura simple para los servicios del ciclo de vida, que necesitan la reserva sin sus reglas. */
    @Transactional(readOnly = true)
    public Booking require(UUID bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
    }

    /**
     * Dentro de la ventana ya no se cancela, pero al profesor no se le deja sin salida: puede pedir
     * reprogramación. Decirle "no puedes" sin decirle "puedes esto otro" es abandonar a alguien con
     * un problema real a dos horas de una clase.
     */
    private String lateCancellationMessage(UserRole role, Duration window) {
        long hours = window.toHours();
        if (role == UserRole.PROFESSOR) {
            return "Faltan menos de " + hours + " horas: ya no puedes cancelar, pero sí proponerle "
                    + "al estudiante otro horario tuyo.";
        }
        return "Faltan menos de " + hours + " horas — la clase se considera impartida (política Orión)";
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
