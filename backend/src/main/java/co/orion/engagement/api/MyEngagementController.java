package co.orion.engagement.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.engagement.application.EngagementQueryService;
import co.orion.identity.application.StudentProfileService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/** Lo que el estudiante lee y equipa de su gamificación. Solo suyo. */
@RestController
@RequestMapping("/api/v1/me")
public class MyEngagementController {

    /** Tope del mapa: pedir dos años de semanas no tiene uso y sí coste. */
    private static final int MAX_SEMANAS = 52;

    private final EngagementQueryService engagement;

    public MyEngagementController(EngagementQueryService engagement) {
        this.engagement = engagement;
    }

    @GetMapping("/engagement")
    public MyEngagementResponse resumen(@AuthenticationPrincipal OrionUserDetails principal) {
        return MyEngagementResponse.from(engagement.resumen(principal.user().getId()));
    }

    @GetMapping("/achievements")
    public List<AchievementResponse> logros(@AuthenticationPrincipal OrionUserDetails principal) {
        return engagement.logros(principal.user().getId()).stream()
                .map(AchievementResponse::from)
                .toList();
    }

    @GetMapping("/cosmetics")
    public List<CosmeticResponse> cosmeticos(@AuthenticationPrincipal OrionUserDetails principal) {
        return engagement.cosmeticos(principal.user().getId()).stream()
                .map(CosmeticResponse::from)
                .toList();
    }

    @PutMapping("/cosmetics")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void equipar(@AuthenticationPrincipal OrionUserDetails principal,
                        @Valid @RequestBody EquipCosmeticsRequest body) {
        engagement.equipar(
                principal.user().getId(),
                body.frameCode(),
                body.paletteCode(),
                body.skyCode(),
                body.accessories() == null ? List.of() : body.accessories().stream()
                        .map(a -> new StudentProfileService.StudentAccessoryView(
                                a.zone(), a.accessoryCode()))
                        .toList());
    }

    @GetMapping("/streak")
    public MyStreakResponse racha(@AuthenticationPrincipal OrionUserDetails principal,
                                  @RequestParam(defaultValue = "12") int weeks) {
        int pedidas = Math.min(Math.max(weeks, 1), MAX_SEMANAS);
        return MyStreakResponse.from(
                engagement.mapaDeConstancia(principal.user().getId(), pedidas));
    }
}
