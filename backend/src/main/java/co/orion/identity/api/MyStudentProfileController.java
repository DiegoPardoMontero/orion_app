package co.orion.identity.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.StudentProfileService;
import co.orion.identity.domain.ProficiencyLevel;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/** La ficha propia del estudiante. Solo del estudiante: la ruta ya lo exige. */
@RestController
@RequestMapping("/api/v1/me/student-profile")
public class MyStudentProfileController {

    private final StudentProfileService profiles;

    public MyStudentProfileController(StudentProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public StudentProfileResponse mine(@AuthenticationPrincipal OrionUserDetails principal) {
        profiles.ensureProfile(principal.user().getId());
        return StudentProfileResponse.own(profiles.own(principal.user().getId()));
    }

    @PutMapping
    public StudentProfileResponse update(@AuthenticationPrincipal OrionUserDetails principal,
                                         @Valid @RequestBody StudentProfileRequest body) {
        return StudentProfileResponse.own(profiles.update(
                principal.user().getId(),
                parseLevel(body.selfDeclaredLevel()),
                body.primaryLanguage(),
                body.motivation(),
                body.goalCodes()));
    }

    @PutMapping("/visibility")
    public StudentProfileResponse visibility(@AuthenticationPrincipal OrionUserDetails principal,
                                             @Valid @RequestBody StudentVisibilityRequest body) {
        return StudentProfileResponse.own(profiles.setVisibility(
                principal.user().getId(), body.isPublic(), body.birthDate()));
    }

    /** Un nivel desconocido es un 422 con nombres, no un 500 con un stack trace. */
    private ProficiencyLevel parseLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ProficiencyLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new UnprocessableException(
                    "Nivel desconocido. Usa BEGINNER, INTERMEDIATE o ADVANCED.");
        }
    }
}
