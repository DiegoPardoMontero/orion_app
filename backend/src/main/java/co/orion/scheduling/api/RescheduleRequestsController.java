package co.orion.scheduling.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import co.orion.scheduling.application.RescheduleRequestService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/**
 * Reprogramar es negociar: uno propone, el otro acepta. El endpoint viejo que movía la clase de
 * forma unilateral ya no existe.
 */
@RestController
public class RescheduleRequestsController {

    private final RescheduleRequestService reschedules;

    public RescheduleRequestsController(RescheduleRequestService reschedules) {
        this.reschedules = reschedules;
    }

    @PostMapping("/api/v1/bookings/{id}/reschedule-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RescheduleRequestResponse propose(@AuthenticationPrincipal OrionUserDetails principal,
                                             @PathVariable UUID id,
                                             @Valid @RequestBody ProposeRescheduleRequest body) {
        return RescheduleRequestResponse.of(
                reschedules.propose(principal.user(), id, body.startsAt().toInstant(), body.reason()),
                principal.user().getId());
    }

    @GetMapping("/api/v1/bookings/{id}/reschedule-requests")
    public List<RescheduleRequestResponse> ofBooking(@AuthenticationPrincipal OrionUserDetails principal,
                                                     @PathVariable UUID id) {
        return reschedules.ofBooking(id).stream()
                .map(request -> RescheduleRequestResponse.of(request, principal.user().getId()))
                .toList();
    }

    /** Las propuestas vivas de quien consulta, sean suyas o por responder. Alimenta el aviso. */
    @GetMapping("/api/v1/me/reschedule-requests")
    public List<RescheduleRequestResponse> mine(@AuthenticationPrincipal OrionUserDetails principal) {
        return reschedules.pendingFor(principal.user().getId()).stream()
                .map(request -> RescheduleRequestResponse.of(request, principal.user().getId()))
                .toList();
    }

    @PostMapping("/api/v1/reschedule-requests/{id}/accept")
    public BookingResponse accept(@AuthenticationPrincipal OrionUserDetails principal,
                                  @PathVariable UUID id) {
        return BookingResponse.from(reschedules.accept(principal.user(), id));
    }

    @PostMapping("/api/v1/reschedule-requests/{id}/decline")
    public RescheduleRequestResponse decline(@AuthenticationPrincipal OrionUserDetails principal,
                                             @PathVariable UUID id) {
        return RescheduleRequestResponse.of(
                reschedules.decline(principal.user(), id), principal.user().getId());
    }
}
