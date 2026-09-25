package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;

@Service
public class BookingQueryService {

    /** Una reserva sigue viva mientras esté confirmada o en pleno pago. */
    private static final List<BookingStatus> ACTIVE_STATUSES =
            List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT);

    public enum Scope {
        UPCOMING,
        PAST
    }

    /**
     * Una clase sigue en «Próximas» —con su botón para entrar— hasta que le quedan cinco minutos
     * (Pardo, 25/09/2026). Antes salía apenas empezaba, y el botón con ella: a quien se le caía la
     * conexión o llegaba un minuto tarde no le quedaba por dónde volver a entrar, aunque la sala
     * siguiera abierta. No es un ajuste: es cómo se agrupa una lista, no una regla de negocio.
     */
    public static final Duration PASA_A_PASADAS_ANTES_DEL_FINAL = Duration.ofMinutes(5);

    private final BookingRepository bookings;
    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final BookingService bookingService;
    private final Clock clock;

    public BookingQueryService(BookingRepository bookings,
                               UserRepository users,
                               ProfessorProfileRepository profiles,
                               BookingService bookingService,
                               Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.profiles = profiles;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MyBookingsView> myBookings(User viewer, Scope scope) {
        Instant now = clock.instant();
        boolean asStudent = viewer.getRole() == UserRole.STUDENT;
        // La política de cancelación se pregunta una vez por petición, no por reserva.
        Duration window = bookingService.cancellationWindowFor(viewer.getRole());

        Instant corte = now.plus(PASA_A_PASADAS_ANTES_DEL_FINAL);
        List<Booking> found = switch (scope) {
            case UPCOMING -> asStudent
                    ? bookings.findByStudentIdAndStatusInAndEndsAtAfterOrderByStartsAtAsc(
                            viewer.getId(), ACTIVE_STATUSES, corte)
                    : bookings.findByProfessorIdAndStatusInAndEndsAtAfterOrderByStartsAtAsc(
                            viewer.getId(), ACTIVE_STATUSES, corte);
            case PAST -> asStudent
                    ? bookings.findEndedOfStudent(viewer.getId(), ACTIVE_STATUSES, corte)
                    : bookings.findEndedOfProfessor(viewer.getId(), ACTIVE_STATUSES, corte);
        };

        Map<UUID, User> counterparts = loadCounterparts(found, asStudent);
        // La foto/titular solo existen para profesores: si quien consulta es estudiante, su
        // contraparte es la profesora y cargamos su perfil público.
        Map<UUID, ProfessorProfile> professorProfiles = asStudent
                ? profiles.findAllById(counterparts.keySet()).stream()
                        .collect(Collectors.toMap(ProfessorProfile::getUserId, Function.identity()))
                : Map.of();

        return found.stream()
                .map(booking -> {
                    UUID counterpartId = asStudent ? booking.getProfessorId() : booking.getStudentId();
                    User counterpart = counterparts.get(counterpartId);
                    ProfessorProfile profile = professorProfiles.get(counterpartId);
                    return new MyBookingsView(
                            booking,
                            counterpart,
                            // La foto ahora vive en el usuario (sirve para estudiantes y profesores);
                            // el titular sigue viniendo del perfil público del profesor.
                            counterpart != null ? counterpart.getPhotoUrl() : null,
                            profile != null ? profile.getHeadline() : null,
                            now,
                            window);
                })
                .toList();
    }

    /**
     * Una consulta por lote para todas las contrapartes, en vez de un join.
     * Decisión consciente: a nuestro volumen (decenas de clases por usuario) esto es una consulta
     * extra y cero complejidad; un join nos ataría scheduling a las tablas de identity.
     */
    private Map<UUID, User> loadCounterparts(List<Booking> found, boolean asStudent) {
        List<UUID> ids = found.stream()
                .map(booking -> asStudent ? booking.getProfessorId() : booking.getStudentId())
                .distinct()
                .toList();

        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
