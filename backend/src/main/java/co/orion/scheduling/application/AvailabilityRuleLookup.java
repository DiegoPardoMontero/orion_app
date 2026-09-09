package co.orion.scheduling.application;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.application.ProfessorAvailabilityLookup;
import co.orion.scheduling.domain.AvailabilityMatcher;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;

/**
 * Implementa para {@code identity} la pregunta «quién suele tener hueco el martes por la noche».
 *
 * <p><strong>Filtra sobre las reglas publicadas, no sobre cupos libres calculados.</strong> Es una
 * decisión, y la razón es que un cupo libre depende del instante y de las reservas vivas: filtrar
 * por eso obligaría a correr {@code SlotCalculator} sobre cada profesor de cada página, y el
 * resultado cambiaría entre la búsqueda y el clic. La regla contesta «este profesor suele tener
 * martes por la noche», que es la pregunta que de verdad se hace quien busca. La disponibilidad
 * exacta se ve al entrar al perfil, donde ya se calcula bien.
 */
@Component
class AvailabilityRuleLookup implements ProfessorAvailabilityLookup {

    private static final Duration CLASS_LENGTH = Duration.ofHours(1);

    private final AvailabilityRuleRepository rules;

    AvailabilityRuleLookup(AvailabilityRuleRepository rules) {
        this.rules = rules;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> professorsAvailable(Set<DayOfWeek> days, LocalTime from, LocalTime to) {
        var candidatas = days == null || days.isEmpty()
                ? rules.findByActiveTrue()
                : rules.findByWeekdayInAndActiveTrue(days);

        return candidatas.stream()
                .filter(r -> AvailabilityMatcher.cabeUnaClase(
                        r.getStartTime(), r.getEndTime(), from, to, CLASS_LENGTH))
                .map(r -> r.getProfessorId())
                .distinct()
                .toList();
    }
}
