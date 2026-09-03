package co.orion.lifecycle.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.lifecycle.application.DisputeQueryService;
import co.orion.lifecycle.application.DisputeService;
import co.orion.lifecycle.application.DisputeView;
import co.orion.lifecycle.application.JobRunRegistry;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/** Los reclamos y el pulso de los jobs: las dos cosas que el admin tiene que poder mirar a diario. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminDisputesController {

    private final DisputeService disputes;
    private final DisputeQueryService disputeQueries;
    private final JobRunRegistry jobs;

    public AdminDisputesController(DisputeService disputes,
                                   DisputeQueryService disputeQueries,
                                   JobRunRegistry jobs) {
        this.disputes = disputes;
        this.disputeQueries = disputeQueries;
        this.jobs = jobs;
    }

    @GetMapping("/disputes")
    public List<DisputeResponse> list(@RequestParam(required = false) String status) {
        return disputeQueries.search(status).stream().map(AdminDisputesController::toResponse).toList();
    }

    /** Tomarlo evita que dos personas resuelvan el mismo reclamo a la vez. */
    @PostMapping("/disputes/{id}/take")
    public DisputeResponse take(@AuthenticationPrincipal OrionUserDetails principal,
                                @PathVariable UUID id) {
        disputes.take(principal.user(), id);
        return single(id);
    }

    @PostMapping("/disputes/{id}/resolve")
    public DisputeResponse resolve(@AuthenticationPrincipal OrionUserDetails principal,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody ResolveDisputeRequest body) {
        disputes.resolve(principal.user(), id, body.outcome(), body.note());
        return single(id);
    }

    /**
     * La última corrida de cada job. El de autocompletado es el que le paga a los profesores: si se
     * detiene, el síntoma tarda semanas en aparecer y llega como "no me han pagado".
     */
    @GetMapping("/jobs/status")
    public List<JobRunRegistry.JobRun> jobStatus() {
        return jobs.all();
    }

    private DisputeResponse single(UUID id) {
        return disputeQueries.search(null).stream()
                .filter(view -> view.dispute().getId().equals(id))
                .findFirst()
                .map(AdminDisputesController::toResponse)
                .orElseThrow();
    }

    private static DisputeResponse toResponse(DisputeView view) {
        return DisputeResponse.of(view.dispute(), view.classAt(),
                view.studentName(), view.professorName(), view.amountCop());
    }
}
