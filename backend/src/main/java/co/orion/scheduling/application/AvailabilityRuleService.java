package co.orion.scheduling.application;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;

@Service
public class AvailabilityRuleService {

    private final AvailabilityRuleRepository rules;

    public AvailabilityRuleService(AvailabilityRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityRule> listOwnRules(UUID professorId) {
        return rules.findByProfessorIdOrderByWeekdayAscStartTimeAsc(professorId);
    }

    @Transactional
    public AvailabilityRule create(UUID professorId, int weekday, LocalTime startTime, LocalTime endTime) {
        DayOfWeek day = DayOfWeek.of(weekday);
        requireStartBeforeEnd(startTime, endTime);
        requireHourOrHalfHour(startTime);
        requireHourOrHalfHour(endTime);

        if (rules.overlapsActiveRule(professorId, day, startTime, endTime)) {
            throw new BusinessRuleViolationException(
                    "Esta franja se cruza con otra que ya tienes ese día.");
        }
        return rules.save(new AvailabilityRule(professorId, day, startTime, endTime));
    }

    /**
     * Borra solo si la regla es del profesor que la pide. Si es ajena responde 404, no 403:
     * un 403 le confirmaría al que pregunta que ese id existe.
     */
    @Transactional
    public void delete(UUID professorId, UUID ruleId) {
        AvailabilityRule rule = rules.findById(ruleId)
                .filter(candidate -> candidate.getProfessorId().equals(professorId))
                .orElseThrow(() -> new ResourceNotFoundException("Regla no encontrada"));
        rules.delete(rule);
    }

    private void requireStartBeforeEnd(LocalTime startTime, LocalTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new BusinessRuleViolationException("La hora de fin tiene que ser después de la hora de inicio.");
        }
    }

    /**
     * Los cupos arrancan cada media hora desde el inicio de la franja (ver {@code SlotCalculator}),
     * así que una franja que empieza o acaba a la media hora cae en la misma rejilla que las demás.
     * Lo que no se admite es cualquier otro minuto: una franja de 18:15 daría cupos a las 18:15 y
     * 18:45, fuera de la rejilla que ve el estudiante y que usa el buscador.
     */
    private void requireHourOrHalfHour(LocalTime time) {
        if (time.getMinute() % 30 != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw new BusinessRuleViolationException(
                    "Las franjas empiezan y terminan a la hora en punto o a la media hora.");
        }
    }
}
