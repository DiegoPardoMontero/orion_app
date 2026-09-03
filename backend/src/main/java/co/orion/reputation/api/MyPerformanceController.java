package co.orion.reputation.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.reputation.application.SanctionService;
import co.orion.reputation.persistence.ProfessorMetricsRepository;
import co.orion.shared.security.OrionUserDetails;

/** "Mi desempeño": lo que el profesor puede ver de sí mismo, y de nadie más. */
@RestController
@RequestMapping("/api/v1/me")
public class MyPerformanceController {

    private final ProfessorMetricsRepository metrics;
    private final SanctionService sanctions;

    public MyPerformanceController(ProfessorMetricsRepository metrics, SanctionService sanctions) {
        this.metrics = metrics;
        this.sanctions = sanctions;
    }

    @GetMapping("/performance")
    public PerformanceResponse myPerformance(@AuthenticationPrincipal OrionUserDetails principal) {
        var id = principal.user().getId();
        return PerformanceResponse.of(
                metrics.findById(id).orElse(null),
                // Se muestran también las propuestas: si el sistema cree que corresponde algo, el
                // profesor merece verlo venir en vez de enterarse cuando ya está aplicado.
                sanctions.historyFor(id));
    }
}
