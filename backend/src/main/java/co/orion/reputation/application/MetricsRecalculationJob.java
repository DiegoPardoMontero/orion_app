package co.orion.reputation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.reputation.domain.ProfessorMetrics;
import co.orion.reputation.domain.RankingCalculator;
import co.orion.reputation.domain.RankingInputs;
import co.orion.reputation.domain.RankingWeights;
import co.orion.reputation.persistence.ProfessorMetricsRepository;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.ProfessorAbsenceRepository;
import co.orion.scheduling.persistence.RescheduleRequestRepository;

/**
 * Recalcula el desempeño de todos los profesores una vez por noche, sobre una ventana móvil de 90
 * días, y con él el puntaje que ordena el buscador.
 *
 * De noche y no en cada búsqueda: el buscador tiene que ordenar por una columna indexada, no hacer
 * aritmética de reputación mientras alguien espera resultados.
 */
@Component
public class MetricsRecalculationJob {

    private static final Logger log = LoggerFactory.getLogger(MetricsRecalculationJob.class);
    public static final String JOB_NAME = "professor-metrics";

    private static final String WINDOW_DAYS = "metrics_window_days";
    private static final String COLD_START = "ranking_cold_start_lessons";

    private final ProfessorProfileRepository profiles;
    private final ProfessorMetricsRepository metrics;
    private final BookingRepository bookings;
    private final ProfessorAbsenceRepository absences;
    private final RescheduleRequestRepository reschedules;
    private final SanctionService sanctions;
    private final PlatformSettingsService settings;
    private final Clock clock;

    public MetricsRecalculationJob(ProfessorProfileRepository profiles,
                                   ProfessorMetricsRepository metrics,
                                   BookingRepository bookings,
                                   ProfessorAbsenceRepository absences,
                                   RescheduleRequestRepository reschedules,
                                   SanctionService sanctions,
                                   PlatformSettingsService settings,
                                   Clock clock) {
        this.profiles = profiles;
        this.metrics = metrics;
        this.bookings = bookings;
        this.absences = absences;
        this.reschedules = reschedules;
        this.sanctions = sanctions;
        this.settings = settings;
        this.clock = clock;
    }

    /** 03:00 de Bogotá: nadie reservando, nadie mirando el buscador. */
    @Scheduled(cron = "${orion.jobs.metrics.cron:0 0 3 * * *}", zone = "America/Bogota")
    public void nightly() {
        recalculateAll();
    }

    @Transactional
    public int recalculateAll() {
        int windowDays = settings.getInt(WINDOW_DAYS);
        Instant since = clock.instant().minus(Duration.ofDays(windowDays));

        List<ProfessorProfile> all = profiles.findAll();
        if (all.isEmpty()) {
            return 0;
        }

        List<RankingInputs> inputs = all.stream()
                .map(profile -> measure(profile, since))
                .toList();

        RankingCalculator calculator = new RankingCalculator(settings.getInt(COLD_START));
        Map<UUID, BigDecimal> scores = calculator.scoreAll(inputs, weights());

        Instant now = clock.instant();
        for (RankingInputs professor : inputs) {
            ProfessorMetrics row = metrics.findById(professor.professorId())
                    .orElseGet(() -> new ProfessorMetrics(professor.professorId()));
            row.recomputePerformance(
                    professor.lessonsCompleted(),
                    professor.attendanceRate(),
                    cancellationRate(professor.professorId(), since),
                    rescheduleRate(professor.professorId(), since),
                    professor.responseRate(),
                    null,
                    professor.activeStudents(),
                    (short) professor.profileCompleteness(),
                    scores.get(professor.professorId()),
                    (short) windowDays,
                    now);
            metrics.save(row);
        }
        log.info("Métricas recalculadas para {} profesor(es) (ventana de {} días)", inputs.size(), windowDays);
        return inputs.size();
    }

    private RankingInputs measure(ProfessorProfile profile, Instant since) {
        UUID id = profile.getUserId();

        long completed = bookings.countByProfessorIdAndStatusInAndStartsAtAfter(
                id, List.of(BookingStatus.COMPLETED, BookingStatus.NO_SHOW_STUDENT), since);
        long missed = absences.countByProfessorIdAndOccurredAtAfter(id, since);

        ProfessorMetrics existing = metrics.findById(id).orElse(null);

        return new RankingInputs(
                id,
                existing != null ? existing.getRatingAvg() : null,
                existing != null ? existing.getRatingCount() : 0,
                (int) completed,
                ratio(completed, completed + missed),
                // La tasa de respuesta necesita la mensajería y todavía no se mide: null significa
                // "no se sabe" y el calculador lo trata como neutro, no como cero.
                null,
                completenessOf(profile),
                (int) bookings.countDistinctStudentsOfProfessorSince(id, since),
                sanctions.activeCountFor(id));
    }

    private BigDecimal cancellationRate(UUID professorId, Instant since) {
        long total = bookings.countByProfessorIdAndStartsAtAfter(professorId, since);
        long cancelled = bookings.countByProfessorIdAndStatusInAndStartsAtAfter(
                professorId, List.of(BookingStatus.CANCELLED_BY_PROFESSOR), since);
        return ratio(cancelled, total);
    }

    private BigDecimal rescheduleRate(UUID professorId, Instant since) {
        long total = bookings.countByProfessorIdAndStartsAtAfter(professorId, since);
        long asked = reschedules.countRequestedByProfessorSince(professorId, since);
        return ratio(asked, total);
    }

    /** Porcentaje 0–100, o null cuando no hay nada que medir: cero mentiría. */
    private BigDecimal ratio(long part, long total) {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Cuán completo está el perfil, sobre ocho campos que de verdad le sirven a un estudiante para
     * elegir. No es burocracia: un perfil vacío no se puede comparar con nada.
     */
    private int completenessOf(ProfessorProfile profile) {
        int filled = 0;
        if (profile.getHeadline() != null && !profile.getHeadline().isBlank()) filled++;
        if (profile.getBio() != null && !profile.getBio().isBlank()) filled++;
        if (profile.getHourlyRateCop() != null) filled++;
        if (profile.getYearsExperience() != null) filled++;
        if (profile.getEducation() != null && !profile.getEducation().isBlank()) filled++;
        if (profile.getCountryCode() != null) filled++;
        if (profile.getNativeLanguage() != null) filled++;
        if (profile.isCertified()) filled++;
        return filled * 100 / 8;
    }

    private RankingWeights weights() {
        return new RankingWeights(
                settings.getInt("ranking_weight_rating"),
                settings.getInt("ranking_weight_attendance"),
                settings.getInt("ranking_weight_response"),
                settings.getInt("ranking_weight_lessons"),
                settings.getInt("ranking_weight_completeness"),
                settings.getInt("ranking_weight_retention"),
                settings.getInt("sanction_penalty_points"));
    }
}
