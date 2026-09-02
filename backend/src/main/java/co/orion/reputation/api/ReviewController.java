package co.orion.reputation.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.reputation.application.ReviewService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/**
 * Reseñas del marketplace. Crear es del estudiante de la reserva; reportar, del profesor reseñado.
 * El listado por profesor es público (parte del perfil que ve un visitante antes de registrarse).
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/bookings/{bookingId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@AuthenticationPrincipal OrionUserDetails principal,
                                 @PathVariable UUID bookingId,
                                 @Valid @RequestBody CreateReviewRequest body) {
        return ReviewResponse.from(
                reviews.create(principal.user(), bookingId, body.rating(), body.comment()));
    }

    @GetMapping("/professors/{professorId}/reviews")
    public PagedReviews byProfessor(@PathVariable UUID professorId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        return reviews.publicReviews(professorId, page, size);
    }

    @PostMapping("/reviews/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@AuthenticationPrincipal OrionUserDetails principal,
                       @PathVariable UUID id,
                       @Valid @RequestBody ReportReviewRequest body) {
        reviews.report(principal.user(), id, body.reason());
    }
}
