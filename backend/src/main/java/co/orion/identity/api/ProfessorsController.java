package co.orion.identity.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.ProfessorProfileService;

/** Directorio público (para cualquier usuario autenticado) de profesores publicados. */
@RestController
@RequestMapping("/api/v1/professors")
public class ProfessorsController {

    private final ProfessorProfileService profileService;

    public ProfessorsController(ProfessorProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public List<ProfessorSummary> list() {
        return profileService.listPublished().stream()
                .map(ProfessorSummary::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProfessorDetail detail(@PathVariable UUID id) {
        return ProfessorDetail.from(profileService.getPublished(id));
    }
}
