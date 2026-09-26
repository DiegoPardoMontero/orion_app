package co.orion.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.Payment;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.domain.PayoutStatus;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;

/** "Mis ganancias" del profesor: cuánto lleva ganado, en qué estado y por qué clase. */
@Service
public class EarningsService {

    /** En una liquidación que todavía no se paga: borrador, aprobada, retenida o arrastrada. */
    private static final List<PayoutStatus> EN_LIQUIDACION =
            List.of(PayoutStatus.DRAFT, PayoutStatus.APPROVED, PayoutStatus.ON_HOLD, PayoutStatus.CARRIED_OVER);


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
        long inTransit = payments.sumInTransit(professorId, fromInstant, toInstant);

        List<Payment> found = payments
                .findByProfessorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        professorId, fromInstant, toInstant);

        return new EarningsSummary(
                held, released - transferred - inTransit, inTransit, transferred, lines(found));
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

        // Un pago sigue RELEASED después de liquidado —lo que cambia es la liquidación—, así que la
        // línea mira si va en una: si no, la clase de un pago ya transferido decía «Por cobrar».
        List<UUID> ids = found.stream().map(Payment::getId).toList();
        Set<UUID> transferidos = ids.isEmpty() ? Set.of()
                : Set.copyOf(payments.findInPayoutsWithStatus(ids, List.of(PayoutStatus.PAID)));
        Set<UUID> enCamino = ids.isEmpty() ? Set.of()
                : Set.copyOf(payments.findInPayoutsWithStatus(ids, EN_LIQUIDACION));

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
                            estadoDeLaLinea(payment, transferidos, enCamino));
                })
                .toList();
    }

    /** El estado del pago, salvo que ya vaya en una liquidación: «TRANSFERRED» o «IN_TRANSIT». */
    private static String estadoDeLaLinea(Payment payment, Set<UUID> transferidos, Set<UUID> enCamino) {
        if (payment.getStatus() == PaymentStatus.RELEASED) {
            if (transferidos.contains(payment.getId())) {
                return "TRANSFERRED";
            }
            if (enCamino.contains(payment.getId())) {
                return "IN_TRANSIT";
            }
        }
        return payment.getStatus().name();
    }
}
