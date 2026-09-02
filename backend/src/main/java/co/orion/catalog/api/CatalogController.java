package co.orion.catalog.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.catalog.application.CatalogService;

/** Catálogo público: idiomas y objetivos activos para filtros y formularios. */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/languages")
    public List<LanguageResponse> languages() {
        return catalog.activeLanguages().stream().map(LanguageResponse::from).toList();
    }

    @GetMapping("/goals")
    public List<GoalResponse> goals() {
        return catalog.activeGoals().stream().map(GoalResponse::from).toList();
    }
}
