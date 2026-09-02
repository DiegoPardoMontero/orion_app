package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            @RequestParam(defaultValue = "RELEVANCE") ProfessorSortOption sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        ProfessorSearchCriteria criteria = new ProfessorSearchCriteria(
                language, level, goal, minPrice, maxPrice, certified, nativeOnly);
        return search.search(criteria, sort, page, size);
    }

    @GetMapping("/{id}")
    public ProfessorDetail detail(@PathVariable UUID id) {
        return profiles.publicDetail(id);
    }
}
