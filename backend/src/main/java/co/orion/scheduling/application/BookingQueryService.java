package co.orion.scheduling.application;

import java.time.Clock;
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

    public enum Scope {
        UPCOMING,
        PAST
    }

    private final BookingRepository bookings;
    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final Clock clock;

    public BookingQueryService(BookingRepository bookings,
                               UserRepository users,
                               ProfessorProfileRepository profiles,
                               Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MyBookingsView> myBookings(User viewer, Scope scope) {
        Instant now = clock.instant();
        boolean asStudent = viewer.getRole() == UserRole.STUDENT;

        List<Booking> found = switch (scope) {
            case UPCOMING -> asStudent
                    ? bookings.findByStudentIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(
                            viewer.getId(), BookingStatus.CONFIRMED, now)
                    : bookings.findByProfessorIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(
                            viewer.getId(), BookingStatus.CONFIRMED, now);
            case PAST -> asStudent
                    ? bookings.findPastOfStudent(viewer.getId(), now)
                    : bookings.findPastOfProfessor(viewer.getId(), now);
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
                    ProfessorProfile profile = professorProfiles.get(counterpartId);
                    return new MyBookingsView(
                            booking,
                            counterparts.get(counterpartId),
                            profile != null ? profile.getPhotoUrl() : null,
                            profile != null ? profile.getHeadline() : null,
                            now);
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
