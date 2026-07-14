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
        requireWholeHour(startTime, "startTime");
        requireWholeHour(endTime, "endTime");

        if (rules.overlapsActiveRule(professorId, day, startTime, endTime)) {
            throw new BusinessRuleViolationException(
                    "La franja se solapa con otra regla activa del mismo día");
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
            throw new BusinessRuleViolationException("startTime debe ser anterior a endTime");
        }
    }

    /** Los cupos son de 60 minutos alineados a la hora, así que las reglas empiezan y acaban en :00. */
    private void requireWholeHour(LocalTime time, String field) {
        if (time.getMinute() != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw new BusinessRuleViolationException(field + " debe estar alineado a la hora en punto (:00)");
        }
    }
}
