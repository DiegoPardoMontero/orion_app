package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import co.orion.shared.error.UnprocessableException;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.ProfessorProfileService;
import co.orion.identity.application.ProfessorSearchCriteria;
import co.orion.identity.application.ProfessorSearchService;
import co.orion.identity.application.ProfessorSortOption;

/**
 * Marketplace público. GET /professors busca con filtros combinables; /professors/{id} da el
 * detalle. Ambos son públicos (un visitante explora antes de registrarse). Nunca devuelven a un
 * profesor no publicado, ni a un COMMISSION sin tarifa.
 */
@RestController
@RequestMapping("/api/v1/professors")
public class ProfessorsController {

    private final ProfessorSearchService search;
    private final ProfessorProfileService profiles;

    public ProfessorsController(ProfessorSearchService search, ProfessorProfileService profiles) {
        this.search = search;
        this.profiles = profiles;
    }

    @GetMapping
    public PagedProfessors list(
            @RequestParam(required = false) String language,
            @RequestParam(required = false) List<String> level,
            @RequestParam(required = false) List<String> goal,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false, name = "native") Boolean nativeOnly,
            @RequestParam(required = false) Boolean certified,
            @RequestParam(required = false) List<String> day,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "RELEVANCE") ProfessorSortOption sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        ProfessorSearchCriteria criteria = ProfessorSearchCriteria.of(
                language, level, goal, minPrice, maxPrice, certified, nativeOnly,
                parseDays(day), parseTime(from, "from"), parseTime(to, "to"));
        return search.search(criteria, sort, page, size);
    }

    @GetMapping("/{id}")
    public ProfessorDetail detail(@PathVariable UUID id) {
        return profiles.publicDetail(id);
    }

    /**
     * «MONDAY», «TUESDAY»… Un día que no existe responde 422 con los válidos, no un 500: escribir
     * mal un parámetro es un error de entrada, no una avería.
     */
    private Set<DayOfWeek> parseDays(List<String> days) {
        if (days == null || days.isEmpty()) {
            return Set.of();
        }
        Set<DayOfWeek> parsed = EnumSet.noneOf(DayOfWeek.class);
        for (String day : days) {
            try {
                parsed.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new UnprocessableException("Ese día no existe: " + day
                        + ". Válidos: " + Arrays.toString(DayOfWeek.values()));
            }
        }
        return parsed;
    }

    /** «18:00», en hora de Bogotá, que es la única hora en la que Orión razona. */
    private LocalTime parseTime(String value, String campo) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new UnprocessableException(
                    "La hora de «" + campo + "» debe ir como HH:mm, por ejemplo 18:00.");
        }
    }
}
