package co.orion.assessment.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.assessment.application.AssessmentBudgetService;
import co.orion.assessment.domain.AssessmentStatus;
import co.orion.assessment.persistence.AiUsageLogRepository;
import co.orion.assessment.persistence.AssessmentRecommendationRepository;
import co.orion.assessment.persistence.ConfidenceAssessmentRepository;
import co.orion.shared.time.BusinessZone;

/**
 * Qué está haciendo el diagnóstico, para quien tiene que decidir si se sostiene.
 *
 * <p>Tres números y no más: cuántos se hacen, cuántos terminan en reserva y cuánto cuesta. El
 * segundo es el que dice si esta función le sirve al negocio — una conversación preciosa que no
 * lleva a ninguna clase es un gasto con buena prensa.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAssessmentController {

    private final ConfidenceAssessmentRepository assessments;
    private final AssessmentRecommendationRepository recommendations;
    private final AiUsageLogRepository usage;
    private final AssessmentBudgetService budget;

    public AdminAssessmentController(ConfidenceAssessmentRepository assessments,
                                     AssessmentRecommendationRepository recommendations,
                                     AiUsageLogRepository usage,
                                     AssessmentBudgetService budget) {
        this.assessments = assessments;
        this.recommendations = recommendations;
        this.usage = usage;
        this.budget = budget;
    }

    @GetMapping("/assessments")
    public ResumenResponse resumen(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        LocalDate desde = from != null ? from : LocalDate.now(BusinessZone.BOGOTA).minusDays(29);
        Instant inicio = desde.atStartOfDay(BusinessZone.BOGOTA).toInstant();

        List<?> todas = assessments.findAll();
        long enRango = assessments.countByStartedAtGreaterThanEqual(inicio);
        long completadas = assessments.findAll().stream()
                .filter(a -> a.getStatus() == AssessmentStatus.COMPLETED
                        && a.getStartedAt().isAfter(inicio))
                .count();
        long conReserva = recommendations.findAll().stream()
                .filter(r -> r.getBookedAt() != null)
                .count();

        return new ResumenResponse(desde, enRango, completadas, conReserva,
                budget.gastadoHoy(), budget.disponible(), todas.size());
    }

    /**
     * @param feature qué parte del producto gastó. Hoy solo el diagnóstico; se deja el filtro
     *                porque el registro es general y mañana habrá más consumidores de IA.
     */
    @GetMapping("/ai/usage")
    public GastoResponse gasto(
            @RequestParam(defaultValue = "DIAGNOSTICO") String feature,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate desde = from != null ? from : LocalDate.now(BusinessZone.BOGOTA).minusDays(29);
        LocalDate hasta = to != null ? to : LocalDate.now(BusinessZone.BOGOTA);

        long gastado = usage.spentBetween(feature,
                desde.atStartOfDay(BusinessZone.BOGOTA).toInstant(),
                hasta.plusDays(1).atStartOfDay(BusinessZone.BOGOTA).toInstant());

        return new GastoResponse(feature, desde, hasta, gastado);
    }

    public record ResumenResponse(LocalDate desde, long iniciados, long completados,
                                  long terminaronEnReserva, long gastadoHoyCop,
                                  boolean disponible, long historicoTotal) {
    }

    public record GastoResponse(String feature, LocalDate desde, LocalDate hasta, long gastadoCop) {
    }
}
