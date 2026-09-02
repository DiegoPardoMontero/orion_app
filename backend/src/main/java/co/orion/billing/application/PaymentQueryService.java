package co.orion.billing.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.data.jpa.domain.Specification;
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
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;

/** Lecturas del libro contable: el estudiante ve lo suyo, el admin lo ve todo. */
@Service
public class PaymentQueryService {

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final UserRepository users;

    public PaymentQueryService(PaymentRepository payments,
                               BookingRepository bookings,
                               UserRepository users) {
        this.payments = payments;
        this.bookings = bookings;
        this.users = users;
    }

    /**
     * El estado del pago de una reserva. Una reserva ajena responde 404 y no 403, igual que en
     * scheduling: no confirmamos que exista.
     */
    @Transactional(readOnly = true)
    public PaymentView statusOf(User actor, UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .filter(candidate -> canSee(actor, candidate))
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        Payment payment = payments.findByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("La reserva no tiene pago asociado"));

        return view(payment, booking);
    }

    @Transactional(readOnly = true)
    public List<PaymentView> ofStudent(UUID studentId) {
        return decorate(payments.findByStudentIdOrderByCreatedAtDesc(studentId));
    }

    /** Conciliación del admin. Un filtro que no llegó sencillamente no entra en la consulta. */
    @Transactional(readOnly = true)
    public List<PaymentView> search(String statusName,
                                    UUID professorId,
                                    UUID studentId,
                                    LocalDate from,
                                    LocalDate to) {
        PaymentStatus status = parseStatus(statusName);
        Instant fromInstant = from != null
                ? from.atStartOfDay(BusinessZone.BOGOTA).toInstant() : null;
        Instant toInstant = to != null
                ? to.plusDays(1).atStartOfDay(BusinessZone.BOGOTA).toInstant() : null;

        // allOf sobre los filtros que de verdad llegaron. Sin filtros devuelve una Specification
        // sin restricciones — findAll(null) lanza "Specification must not be null".
        Specification<Payment> spec = Specification.allOf(Stream.of(
                        PaymentSpecifications.status(status),
                        PaymentSpecifications.professor(professorId),
                        PaymentSpecifications.student(studentId),
                        PaymentSpecifications.createdFrom(fromInstant),
                        PaymentSpecifications.createdBefore(toInstant))
                .filter(Objects::nonNull)
                .toList());

        return decorate(payments.findAll(spec));
    }

    /** Los pagos que componen una liquidación, ya decorados para el CSV y la pantalla. */
    @Transactional(readOnly = true)
    public List<PaymentView> decorate(List<Payment> found) {
        Map<UUID, Booking> classes = bookings
                .findAllById(found.stream().map(Payment::getBookingId).toList())
                .stream()
                .collect(Collectors.toMap(Booking::getId, Function.identity()));

        List<UUID> peopleIds = found.stream()
                .flatMap(payment -> Stream.of(payment.getStudentId(), payment.getProfessorId()))
                .distinct()
                .toList();
        Map<UUID, User> people = users.findAllById(peopleIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return found.stream()
                .map(payment -> new PaymentView(
                        payment,
                        classes.get(payment.getBookingId()),
                        nameOf(people, payment.getStudentId()),
                        nameOf(people, payment.getProfessorId())))
                .toList();
    }

    private PaymentView view(Payment payment, Booking booking) {
        Map<UUID, User> people = users
                .findAllById(List.of(payment.getStudentId(), payment.getProfessorId())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new PaymentView(payment, booking,
                nameOf(people, payment.getStudentId()),
                nameOf(people, payment.getProfessorId()));
    }

    private String nameOf(Map<UUID, User> people, UUID id) {
        User user = people.get(id);
        return user != null ? user.getFullName() : null;
    }

    private boolean canSee(User actor, Booking booking) {
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case STUDENT -> booking.getStudentId().equals(actor.getId());
            // El profesor ve el estado de la clase, pero el detalle del pago del estudiante no es
            // suyo: lo que él necesita —cuánto gana— está en /me/earnings.
            case PROFESSOR -> false;
        };
    }

    private PaymentStatus parseStatus(String statusName) {
        if (statusName == null || statusName.isBlank()) {
            return null;
        }
        try {
            return PaymentStatus.valueOf(statusName.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("Estado de pago desconocido: " + statusName);
        }
    }
}
