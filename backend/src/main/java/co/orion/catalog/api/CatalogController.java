package co.orion.catalog.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.catalog.application.CatalogService;
import co.orion.catalog.application.PublicFiguresService;

/** Catálogo público: idiomas y objetivos activos para filtros y formularios. */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalog;
    private final PublicFiguresService figures;

    public CatalogController(CatalogService catalog, PublicFiguresService figures) {
        this.catalog = catalog;
        this.figures = figures;
    }

    /**
     * Los números de negocio que se escriben en pantallas y documentos. Público porque ya se
     * anuncian en la portada y en los Términos, y porque la página de «Enseña con Orión» los
     * necesita antes de que nadie inicie sesión.
     */
    @GetMapping("/figures")
    public PublicFigures figures() {
        return figures.figures();
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
