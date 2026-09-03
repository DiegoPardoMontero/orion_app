package co.orion.lifecycle.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.persistence.PaymentRepository;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.lifecycle.domain.Dispute;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.persistence.BookingRepository;

/**
 * Arma la vista de los reclamos con su contexto. El admin no puede decidir sobre un reclamo viendo
 * solo un id: necesita saber de qué clase se trata, entre quiénes y cuánto dinero hay en juego.
 */
@Service
public class DisputeQueryService {

    private final DisputeService disputes;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final PaymentRepository payments;

    public DisputeQueryService(DisputeService disputes,
                               BookingRepository bookings,
                               UserRepository users,
                               PaymentRepository payments) {
        this.disputes = disputes;
        this.bookings = bookings;
        this.users = users;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<DisputeView> search(String status) {
        return decorate(disputes.search(status));
    }

    /** Consultas por lote, como en el resto: una por clases, una por personas, una por pagos. */
    private List<DisputeView> decorate(List<Dispute> found) {
        List<UUID> bookingIds = found.stream().map(Dispute::getBookingId).distinct().toList();

        Map<UUID, Booking> classes = bookings.findAllById(bookingIds).stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));

        List<UUID> peopleIds = classes.values().stream()
                .flatMap(booking -> Stream.of(booking.getStudentId(), booking.getProfessorId()))
                .distinct()
                .toList();
        Map<UUID, User> people = users.findAllById(peopleIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<UUID, Long> amounts = payments.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.toMap(p -> p.getBookingId(), p -> p.getAmountCop()));

        return found.stream()
                .map(dispute -> {
                    Booking booking = classes.get(dispute.getBookingId());
                    return new DisputeView(
                            dispute,
                            booking != null ? booking.getStartsAt() : null,
                            booking != null ? nameOf(people, booking.getStudentId()) : null,
                            booking != null ? nameOf(people, booking.getProfessorId()) : null,
                            amounts.getOrDefault(dispute.getBookingId(), 0L));
                })
                .toList();
    }

    private String nameOf(Map<UUID, User> people, UUID id) {
        User user = people.get(id);
        return user != null ? user.getFullName() : null;
    }
}
