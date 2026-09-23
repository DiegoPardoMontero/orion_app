package co.orion.teaching.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.shared.security.OrionUserDetails;
import co.orion.teaching.application.LessonNoteMetrics;

/**
 * Las cifras del acta (brief del Bloque 10, paso C1): el panel del admin y el porcentaje de clases
 * con acta que el profesor ve en su desempeño. Viven en {@code teaching} para que ningún otro
 * módulo tenga que importarlo.
 */
@RestController
public class LessonNoteMetricsController {

    private final LessonNoteMetrics metricas;

    public LessonNoteMetricsController(LessonNoteMetrics metricas) {
        this.metricas = metricas;
    }

    /** Solo admin: la ruta cuelga de {@code /api/v1/admin/**}. */
    @GetMapping("/api/v1/admin/lesson-notes/metrics")
    public LessonNoteMetrics.Panel panel() {
        return metricas.panel();
    }

    /** Informativo: no alimenta el ranking ni las sanciones, que siguen en modo observación. */
    @GetMapping("/api/v1/professors/me/lesson-notes/share")
    public LessonNoteMetrics.DelProfesor mia(@AuthenticationPrincipal OrionUserDetails principal) {
        return metricas.delProfesor(principal.user().getId());
    }
}
