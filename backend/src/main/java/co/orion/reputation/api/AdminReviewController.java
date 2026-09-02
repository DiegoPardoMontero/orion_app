package co.orion.reputation.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.reputation.application.ReviewService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/** Moderación de reseñas por el admin: ver las reportadas y ocultar (nunca borrar) una reseña. */
@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviews;

    public AdminReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/reported")
    public List<ReportedReviewResponse> reported() {
        return reviews.reportedReviews();
    }

    @PostMapping("/{id}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@AuthenticationPrincipal OrionUserDetails principal,
                     @PathVariable UUID id,
                     @Valid @RequestBody HideReviewRequest body) {
        reviews.hide(id, principal.user().getId(), body.reason());
    }
}
