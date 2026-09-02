package co.orion.identity.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.ProfessorProfileService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/me/profile")
public class MyProfileController {

    private final ProfessorProfileService profileService;

    public MyProfileController(ProfessorProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse myProfile(@AuthenticationPrincipal OrionUserDetails principal) {
        return profileService.getOwnProfile(principal.user().getId());
    }

    @PutMapping
    public ProfileResponse update(@AuthenticationPrincipal OrionUserDetails principal,
                                  @Valid @RequestBody UpdateProfileRequest body) {
        return profileService.updateOwnProfile(principal.user().getId(), body);
    }

    /** Fija la tarifa y responde con el desglose (cuánto retiene Orión, cuánto recibe el profesor). */
    @PutMapping("/rate")
    public RateBreakdownResponse setRate(@AuthenticationPrincipal OrionUserDetails principal,
                                         @Valid @RequestBody RateRequest body) {
        return profileService.setRate(principal.user().getId(), body.hourlyRateCop());
    }

    /** Desglose SIN guardar, para pintarlo mientras el profesor escribe la tarifa. */
    @GetMapping("/rate/preview")
    public RateBreakdownResponse ratePreview(@RequestParam long rate) {
        return profileService.ratePreview(rate);
    }
}
