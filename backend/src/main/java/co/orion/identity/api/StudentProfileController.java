package co.orion.identity.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.StudentProfileService;
import co.orion.shared.security.OrionUserDetails;

/**
 * El perfil de un estudiante visto por otra persona. Las tres capas de visibilidad las aplica el
 * servicio, y cuando no hay derecho a verlo responde 404: un 403 confirmaría que existe.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentProfileController {

    private final StudentProfileService profiles;

    public StudentProfileController(StudentProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/{studentId}/profile")
    public StudentProfileResponse profile(@AuthenticationPrincipal OrionUserDetails principal,
                                          @PathVariable UUID studentId) {
        var ficha = profiles.visibleTo(studentId, principal.user());
        return principal.user().getId().equals(studentId)
                ? StudentProfileResponse.own(ficha)
                : StudentProfileResponse.publicView(ficha);
    }
}
