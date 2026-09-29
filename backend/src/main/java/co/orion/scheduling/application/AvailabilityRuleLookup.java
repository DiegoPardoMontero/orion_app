package co.orion.scheduling.application;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.shared.time.ClassLength;
import co.orion.identity.application.ProfessorAvailabilityLookup;
import co.orion.scheduling.domain.AvailabilityMatcher;
import co.orion.scheduling.domain.AvailabilityMatcher.Tramo;
import co.orion.scheduling.domain.AvailabilityRule;
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

    // Una hora era un número suelto y estaba mal: filtraba fuera del buscador al profesor cuya
    // franja mide exactamente lo que dura una clase. La duración tiene un solo dueño.
    private static final Duration CLASS_LENGTH = ClassLength.DURATION;

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

        return conAlgunTramo(candidatas, t -> AvailabilityMatcher.cabeUnaClase(
                t.inicio(), t.fin(), from, to, CLASS_LENGTH));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> professorsAvailableAt(Set<DayOfWeek> days, Set<LocalTime> hours) {
        var candidatas = days == null || days.isEmpty()
                ? rules.findByActiveTrue()
                : rules.findByWeekdayInAndActiveTrue(days);

        return conAlgunTramo(candidatas, t -> hours.stream().anyMatch(h ->
                AvailabilityMatcher.empiezaALas(t.inicio(), t.fin(), h, CLASS_LENGTH)));
    }

    /**
     * Los profesores con algún tramo que cumple, mirando las franjas de cada día ya fundidas: como las
     * funde el cálculo de cupos, el buscador tiene que ver las mismas tardes que ve el perfil.
     */
    private static List<UUID> conAlgunTramo(List<AvailabilityRule> reglas, Predicate<Tramo> cumple) {
        Map<UUID, Map<DayOfWeek, List<Tramo>>> porProfesorYDia = new LinkedHashMap<>();
        for (AvailabilityRule r : reglas) {
            porProfesorYDia.computeIfAbsent(r.getProfessorId(), k -> new EnumMap<>(DayOfWeek.class))
                    .computeIfAbsent(r.getWeekday(), k -> new ArrayList<>())
                    .add(new Tramo(r.getStartTime(), r.getEndTime()));
        }
        return porProfesorYDia.entrySet().stream()
                .filter(e -> e.getValue().values().stream()
                        .anyMatch(tramos -> AvailabilityMatcher.fundir(tramos).stream().anyMatch(cumple)))
                .map(Map.Entry::getKey)
                .toList();
    }
}
