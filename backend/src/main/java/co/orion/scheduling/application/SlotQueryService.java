package co.orion.scheduling.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.application.ProfessorProfileService;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.domain.Slot;
import co.orion.scheduling.domain.SlotCalculator;
import co.orion.scheduling.persistence.AvailabilityExceptionRepository;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.shared.error.BusinessRuleViolationException;

@Service
public class SlotQueryService {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_RANGE_DAYS = 31;

    private final AvailabilityRuleRepository rules;
    private final AvailabilityExceptionRepository exceptions;
    private final ProfessorProfileService profiles;
    private final SlotCalculator calculator = new SlotCalculator();
    private final Clock clock;

    public SlotQueryService(AvailabilityRuleRepository rules,
                            AvailabilityExceptionRepository exceptions,
                            ProfessorProfileService profiles,
                            Clock clock) {
        this.rules = rules;
        this.exceptions = exceptions;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Slot> availableSlots(UUID professorId, LocalDate from, LocalDate to) {
        // Un profesor no publicado no expone cupos aunque tenga reglas: lanza 404.
        profiles.getPublished(professorId);

        LocalDate start = from != null ? from : today();
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_RANGE_DAYS - 1);
        validateRange(start, end);

        return calculator.calculate(
                rules.findByProfessorIdAndActiveTrue(professorId),
                exceptions.findByProfessorIdAndExceptionDateBetween(professorId, start, end),
                List.of(), // TODO Tarea 3: restar reservas confirmadas
                start,
                end,
                now());
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
