package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.BookingRepository;

@Service
public class AdminBookingService {

    /** Sin paginación formal en el MVP: un tope y un aviso. */
    public static final int MAX_ROWS = 200;

    private static final Duration LAST_WEEK = Duration.ofDays(7);

    private final BookingRepository bookings;
    private final UserRepository users;
    private final Clock clock;

    public AdminBookingService(BookingRepository bookings, UserRepository users, Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.clock = clock;
    }

    /** Una reserva junto a los nombres de sus participantes: lo que la tabla necesita pintar. */
    public record AdminBookingView(Booking booking, User student, User professor) {
    }

    public record Metrics(long bookingsLast7Days, double selfServicePctAllTime) {
    }

    @Transactional(readOnly = true)
    public List<AdminBookingView> search(LocalDate from, LocalDate to,
                                         UUID professorId, BookingStatus status) {
        // Las fechas del filtro son días de Bogotá; el rango es [inicio del día from, inicio del
        // día siguiente a to) para que "to" sea inclusivo sin depender de la hora.
        Instant desde = from == null ? null : from.atStartOfDay(BusinessZone.BOGOTA).toInstant();
        Instant hasta = to == null ? null : to.plusDays(1).atStartOfDay(BusinessZone.BOGOTA).toInstant();

        List<Booking> encontradas = bookings.findAll(
                filtros(desde, hasta, professorId, status),
                PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "startsAt")))
                .getContent();

        Map<UUID, User> porId = cargarParticipantes(encontradas);

        return encontradas.stream()
                .map(booking -> new AdminBookingView(
                        booking,
                        porId.get(booking.getStudentId()),
                        porId.get(booking.getProfessorId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Metrics metrics() {
        return new Metrics(
                bookings.countByCreatedAtGreaterThanEqual(clock.instant().minus(LAST_WEEK)),
                bookings.selfServicePercentage());
    }

    /**
     * Cada filtro se añade solo si llegó. Así la consulta nunca lleva un parámetro nulo, que es
     * lo que Postgres no sabe tipar ("could not determine data type of parameter $1").
     */
    private Specification<Booking> filtros(Instant desde, Instant hasta,
                                           UUID professorId, BookingStatus status) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("startsAt"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThan(root.get("startsAt"), hasta));
            }
            if (professorId != null) {
                condiciones.add(cb.equal(root.get("professorId"), professorId));
            }
            if (status != null) {
                condiciones.add(cb.equal(root.get("status"), status));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    /** Una consulta por lote en vez de un join: mismo criterio que en "mis clases". */
    private Map<UUID, User> cargarParticipantes(List<Booking> encontradas) {
        List<UUID> ids = encontradas.stream()
                .flatMap(booking -> java.util.stream.Stream.of(
                        booking.getStudentId(), booking.getProfessorId()))
                .distinct()
                .toList();

        return users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
