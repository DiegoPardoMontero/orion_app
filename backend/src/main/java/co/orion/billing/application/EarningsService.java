package co.orion.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.Payment;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.BookingRepository;

/** "Mis ganancias" del profesor: cuánto lleva ganado, en qué estado y por qué clase. */
@Service
public class EarningsService {

    private static final int DEFAULT_RANGE_DAYS = 30;

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final Clock clock;

    public EarningsService(PaymentRepository payments,
                           BookingRepository bookings,
                           UserRepository users,
                           Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public EarningsSummary of(UUID professorId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.ofInstant(clock.instant(), BusinessZone.BOGOTA);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1L);

        Instant fromInstant = start.atStartOfDay(BusinessZone.BOGOTA).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(BusinessZone.BOGOTA).toInstant();

        long held = payments.sumEarningsByStatus(professorId, PaymentStatus.PAID, fromInstant, toInstant);
        long released = payments.sumEarningsByStatus(
                professorId, PaymentStatus.RELEASED, fromInstant, toInstant);
        long transferred = payments.sumAlreadyTransferred(professorId, fromInstant, toInstant);

        List<Payment> found = payments
                .findByProfessorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        professorId, fromInstant, toInstant);

        return new EarningsSummary(held, released - transferred, transferred, lines(found));
    }

    /**
     * Una consulta por lote para las clases y otra para los estudiantes, en vez de un join entre
     * módulos: misma decisión consciente que en BookingQueryService y por el mismo motivo.
     */
    private List<EarningLine> lines(List<Payment> found) {
        Map<UUID, Booking> classes = bookings
                .findAllById(found.stream().map(Payment::getBookingId).toList())
                .stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));

        Map<UUID, User> students = users
                .findAllById(found.stream().map(Payment::getStudentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return found.stream()
                .map(payment -> {
                    Booking booking = classes.get(payment.getBookingId());
                    User student = students.get(payment.getStudentId());
                    return new EarningLine(
                            payment.getBookingId(),
                            booking != null ? booking.getStartsAt() : null,
                            student != null ? student.getFullName() : null,
                            payment.getAmountCop(),
                            payment.getCommissionCop(),
                            payment.getProfessorEarningsCop(),
                            payment.getStatus().name());
                })
                .toList();
    }
}
