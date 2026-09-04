package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.application.ProfessorAccessService;
import co.orion.identity.application.ProfessorProfileService;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.OccupiedInterval;
import co.orion.scheduling.domain.Slot;
import co.orion.scheduling.domain.SlotCalculator;
import co.orion.scheduling.persistence.AvailabilityExceptionRepository;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.time.BusinessZone;

@Service
public class SlotQueryService {

    private static final List<BookingStatus> OCCUPYING_STATUSES =
            List.of(BookingStatus.CONFIRMED, BookingStatus.PENDING_PAYMENT);

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_RANGE_DAYS = 31;

    private final AvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final BookingRepository bookings;
    private final ProfessorProfileService profiles;
    private final ProfessorAccessService access;
    private final SlotCalculator calculator = new SlotCalculator();
    private final Clock clock;

    public SlotQueryService(AvailabilityRuleRepository rules,
                            AvailabilityExceptionRepository exceptions,
                            BookingRepository bookings,
                            ProfessorProfileService profiles,
                            ProfessorAccessService access,
                            Clock clock) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.bookings = bookings;
        this.profiles = profiles;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Slot> availableSlots(UUID professorId, LocalDate from, LocalDate to) {
        // Un profesor no visible en el marketplace no expone cupos aunque tenga reglas: lanza 404.
        profiles.ensurePublished(professorId);
        // Y un profesor no aprobado tampoco expone cupos (403): mismo gate que reservar.
        access.assertCanTeach(professorId);

        LocalDate start = from != null ? from : today();
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_RANGE_DAYS - 1);
        validateRange(start, end);

        return calculator.calculate(
                rules.findByProfessorIdAndActiveTrue(professorId),
                exceptions.findByProfessorIdAndExceptionDateBetween(professorId, start, end),
                occupiedByBookings(professorId, start, end),
                start,
                end,
                now());
    }

    /**
     * Ocupan cupo las CONFIRMED y las PENDING_PAYMENT: una reserva cancelada o vencida libera su
     * horario, y por eso el índice único de bookings es parcial sobre ese mismo par de estados.
     * Mostrar como libre un cupo que alguien está pagando llevaría a dos estudiantes al checkout
     * por la misma hora, y el segundo pagaría una clase que ya no existe.
     */
    private List<OccupiedInterval> occupiedByBookings(UUID professorId,
                                                      LocalDate from,
                                                      LocalDate to) {
        Instant rangeStart = from.atStartOfDay(BusinessZone.BOGOTA).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(BusinessZone.BOGOTA).toInstant();

        return bookings
                .findByProfessorIdAndStatusInAndStartsAtBetween(
                        professorId, OCCUPYING_STATUSES, rangeStart, rangeEnd)
                .stream()
                .map(booking -> new OccupiedInterval(
                        booking.getStartsAt().atZone(BusinessZone.BOGOTA),
                        booking.getEndsAt().atZone(BusinessZone.BOGOTA)))
                .toList();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleViolationException("from no puede ser posterior a to");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessRuleViolationException(
                    "El rango no puede superar " + MAX_RANGE_DAYS + " días (pediste " + days + ")");
        }
    }

    /** El reloj entra por el bean Clock, así los tests pueden congelarlo. */
    private ZonedDateTime now() {
        return clock.instant().atZone(BusinessZone.BOGOTA);
    }

    private LocalDate today() {
        return now().toLocalDate();
    }
}
