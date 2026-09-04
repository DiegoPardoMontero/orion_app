package co.orion.scheduling.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.scheduling.domain.AvailabilityException;
import co.orion.scheduling.persistence.AvailabilityExceptionRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.time.BusinessZone;

@Service
public class AvailabilityExceptionService {

    private final AvailabilityExceptionRepository exceptions;
    private final Clock clock;

    public AvailabilityExceptionService(AvailabilityExceptionRepository exceptions, Clock clock) {
        this.exceptions = exceptions;
        this.clock = clock;
    }

    /** Solo las de hoy en adelante: los bloqueos pasados ya no le sirven a nadie. */
    @Transactional(readOnly = true)
    public List<AvailabilityException> listUpcoming(UUID professorId) {
        return exceptions
                .findByProfessorIdAndExceptionDateGreaterThanEqualOrderByExceptionDateAscStartTimeAsc(
                        professorId, today());
    }

    @Transactional
    public AvailabilityException create(UUID professorId, LocalDate date,
                                        LocalTime startTime, LocalTime endTime, String reason) {
        boolean wholeDay = startTime == null && endTime == null;
        if (!wholeDay) {
            if (startTime == null || endTime == null) {
                throw new BusinessRuleViolationException(
                        "startTime y endTime van juntos: ambos presentes (bloqueo parcial) o ambos ausentes (día completo)");
            }
            if (!startTime.isBefore(endTime)) {
                throw new BusinessRuleViolationException("startTime debe ser anterior a endTime");
            }
        }

        AvailabilityException exception = wholeDay
                ? AvailabilityException.wholeDay(professorId, date, reason)
                : AvailabilityException.partial(professorId, date, startTime, endTime, reason);
        return exceptions.save(exception);
    }

    /** Ajena o inexistente → 404, por la misma razón que en las reglas: no filtrar existencia. */
    @Transactional
    public void delete(UUID professorId, UUID exceptionId) {
        AvailabilityException exception = exceptions.findById(exceptionId)
                .filter(candidate -> candidate.getProfessorId().equals(professorId))
                .orElseThrow(() -> new ResourceNotFoundException("Excepción no encontrada"));
        exceptions.delete(exception);
    }

    /** "Hoy" es hoy en Bogotá, no en UTC: a las 19:00 de Bogotá en UTC ya es mañana. */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), BusinessZone.BOGOTA);
    }
}
