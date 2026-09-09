package co.orion.lifecycle.api;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.lifecycle.application.RetractionService;
import co.orion.shared.security.OrionUserDetails;

/** El retracto, desde el lado del estudiante. */
@RestController
@RequestMapping("/api/v1/me/bookings/{bookingId}/retraction")
public class RetractionController {

    private final RetractionService retraction;
    private final Clock clock;

    public RetractionController(RetractionService retraction, Clock clock) {
        this.retraction = retraction;
        this.clock = clock;
    }

    /** Si aplica y hasta cuándo. La tarjeta de la clase decide con esto si enseña el botón. */
    @GetMapping
    public EligibilityResponse eligibility(@AuthenticationPrincipal OrionUserDetails principal,
                                           @PathVariable UUID bookingId) {
        RetractionService.Elegibilidad e =
                retraction.eligibility(bookingId, principal.user().getId());
        return new EligibilityResponse(e.puede(), e.motivo(), e.limite());
    }

    @PostMapping
    public RefundResponse retract(@AuthenticationPrincipal OrionUserDetails principal,
                                  @PathVariable UUID bookingId) {
        return RefundResponse.from(
                retraction.retract(bookingId, principal.user().getId()),
                principal.user().getFullName(), clock.instant());
    }

    public record EligibilityResponse(boolean eligible, String reason,
                                      java.time.Instant deadline) {
    }
}
