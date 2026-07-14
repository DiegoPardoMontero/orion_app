package co.orion.identity.api;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.ProfessorProfileService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/me/profile")
public class MyProfileController {

    private final ProfessorProfileService profileService;

    public MyProfileController(ProfessorProfileService profileService) {
        this.profileService = profileService;
    }

    @PutMapping
    public ProfileResponse update(@AuthenticationPrincipal OrionUserDetails principal,
                                  @Valid @RequestBody UpdateProfileRequest body) {
        return ProfileResponse.from(profileService.updateOwnProfile(
                principal.user().getId(),
                body.headline(),
                body.bio(),
                body.photoUrl(),
                body.isPublished()));
    }
}
