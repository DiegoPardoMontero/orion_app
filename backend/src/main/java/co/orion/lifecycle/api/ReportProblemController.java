package co.orion.lifecycle.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.lifecycle.application.DisputeService;
import co.orion.lifecycle.domain.Dispute;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/**
 * "El profesor no se presentó". Es la única vía del estudiante para que esa clase no se dé por
 * dictada y su dinero no se libere solo.
 */
@RestController
public class ReportProblemController {

    private final DisputeService disputes;

    public ReportProblemController(DisputeService disputes) {
        this.disputes = disputes;
    }

    @PostMapping("/api/v1/bookings/{id}/report-problem")
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeResponse report(@AuthenticationPrincipal OrionUserDetails principal,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody ReportProblemRequest body) {
        Dispute dispute = disputes.report(
                principal.user(), id, body.reason(), body.description());
        return DisputeResponse.of(dispute, null, principal.user().getFullName(), null, 0);
    }
}
