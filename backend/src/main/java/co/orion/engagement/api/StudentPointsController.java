package co.orion.engagement.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import co.orion.engagement.application.EngagementQueryService;
import co.orion.identity.application.StudentProfileService;
import co.orion.shared.security.OrionUserDetails;

/**
 * Los puntos de un estudiante vistos por otra persona, junto a su ficha. Quién puede verlos lo decide
 * la ficha misma: si {@code visibleTo} no deja ver la ficha, tampoco los puntos, con el mismo 404.
 */
@RestController
public class StudentPointsController {

    public record StudentPointsResponse(long total) {
    }

    private final EngagementQueryService engagement;
    private final StudentProfileService fichas;

    public StudentPointsController(EngagementQueryService engagement, StudentProfileService fichas) {
        this.engagement = engagement;
        this.fichas = fichas;
    }

    @GetMapping("/api/v1/students/{id}/points")
    public StudentPointsResponse puntos(@PathVariable UUID id, @AuthenticationPrincipal OrionUserDetails principal) {
        fichas.visibleTo(id, principal.user());
        return new StudentPointsResponse(engagement.totalDe(id));
    }
}
