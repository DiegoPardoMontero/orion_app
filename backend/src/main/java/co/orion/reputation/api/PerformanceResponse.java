package co.orion.reputation.api;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import co.orion.reputation.domain.ProfessorMetrics;
import co.orion.reputation.domain.ProfessorSanction;
import co.orion.shared.time.BusinessZone;

/**
 * El desempeño que ve el profesor. Incluye sus sanciones activas con su motivo y su fecha de fin:
 * una sanción invisible es solo una caída inexplicable de ingresos.
 */
public record PerformanceResponse(BigDecimal ratingAvg,
                                  int ratingCount,
                                  int lessonsCompleted,
                                  BigDecimal attendanceRate,
                                  BigDecimal cancellationRate,
                                  BigDecimal rescheduleRate,
                                  int activeStudents,
                                  Short profileCompleteness,
                                  BigDecimal rankingScore,
                                  short windowDays,
                                  ZonedDateTime computedAt,
                                  List<SanctionView> sanctions) {

    public record SanctionView(UUID id,
                               String type,
                               String reason,
                               String state,
                               ZonedDateTime startsAt,
                               ZonedDateTime endsAt) {

        public static SanctionView from(ProfessorSanction sanction) {
            return new SanctionView(
                    sanction.getId(),
                    sanction.getType().name(),
                    sanction.getReason(),
                    sanction.getState().name(),
                    sanction.getStartsAt().atZone(BusinessZone.BOGOTA),
                    sanction.getEndsAt() != null
                            ? sanction.getEndsAt().atZone(BusinessZone.BOGOTA) : null);
        }
    }

    /** Un profesor sin métricas todavía calculadas no es un error: es uno que acaba de llegar. */
    public static PerformanceResponse of(ProfessorMetrics metrics, List<ProfessorSanction> sanctions) {
        List<SanctionView> views = sanctions.stream().map(SanctionView::from).toList();
        if (metrics == null) {
            return new PerformanceResponse(null, 0, 0, null, null, null, 0, null, null,
                    (short) 90, null, views);
        }
        return new PerformanceResponse(
                metrics.getRatingAvg(),
                metrics.getRatingCount(),
                metrics.getLessonsCompleted(),
                metrics.getAttendanceRate(),
                metrics.getCancellationRate(),
                metrics.getRescheduleRate(),
                metrics.getActiveStudents(),
                metrics.getProfileCompleteness(),
                metrics.getRankingScore(),
                metrics.getWindowDays(),
                metrics.getComputedAt() != null
                        ? metrics.getComputedAt().atZone(BusinessZone.BOGOTA) : null,
                views);
    }
}
