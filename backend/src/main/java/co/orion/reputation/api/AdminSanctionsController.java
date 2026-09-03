package co.orion.reputation.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.reputation.application.SanctionService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/**
 * Las sanciones vistas por el admin. En modo observación esta pantalla es el paso que falta: el
 * sistema propone y aquí una persona confirma o descarta.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminSanctionsController {

    private final SanctionService sanctions;

    public AdminSanctionsController(SanctionService sanctions) {
        this.sanctions = sanctions;
    }

    /** Lo que el sistema propuso y espera decisión. La bandeja del modo observación. */
    @GetMapping("/sanctions/proposed")
    public List<PerformanceResponse.SanctionView> proposed() {
        return sanctions.proposed().stream().map(PerformanceResponse.SanctionView::from).toList();
    }

    @GetMapping("/professors/{id}/sanctions")
    public List<PerformanceResponse.SanctionView> ofProfessor(@PathVariable UUID id) {
        return sanctions.historyFor(id).stream().map(PerformanceResponse.SanctionView::from).toList();
    }

    @PostMapping("/sanctions/{id}/confirm")
    public PerformanceResponse.SanctionView confirm(@AuthenticationPrincipal OrionUserDetails principal,
                                                    @PathVariable UUID id) {
        return PerformanceResponse.SanctionView.from(
                sanctions.confirm(id, principal.user().getId()));
    }

    @PostMapping("/professors/{id}/sanctions")
    public PerformanceResponse.SanctionView apply(@AuthenticationPrincipal OrionUserDetails principal,
                                                  @PathVariable UUID id,
                                                  @Valid @RequestBody ApplySanctionRequest body) {
        return PerformanceResponse.SanctionView.from(
                sanctions.applyManually(id, body.type(), body.reason(), principal.user().getId()));
    }

    /** Levantarla. La fila se conserva: el historial no se borra, se marca. */
    @DeleteMapping("/sanctions/{id}")
    public PerformanceResponse.SanctionView revoke(@AuthenticationPrincipal OrionUserDetails principal,
                                                   @PathVariable UUID id) {
        return PerformanceResponse.SanctionView.from(
                sanctions.revoke(id, principal.user().getId()));
    }
}
