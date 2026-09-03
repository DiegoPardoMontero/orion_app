package co.orion.reputation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * El desempeño de un profesor, materializado. Dos ritmos distintos conviven en esta fila:
 *
 * <ul>
 *   <li>El <b>rating</b> se recalcula al instante al crear u ocultar una reseña, solo sobre las
 *       visibles.</li>
 *   <li>El <b>resto de indicadores y el puntaje de ranking</b> los recalcula un job nocturno sobre
 *       una ventana móvil de 90 días. El buscador ordena por la columna ya calculada: nadie hace
 *       aritmética de reputación en medio de una búsqueda.</li>
 * </ul>
 *
 * El id no se genera en la BD: es el professor_id (uno a uno con el usuario), así que se asigna en
 * el constructor.
 *
 * ratingAvg puede ser null (sin reseñas visibles). La regla de exhibición —no mostrar promedio con
 * menos de 3 reseñas— NO vive aquí: esta fila guarda la verdad cruda; el gate lo aplica quien la lee.
 */
@Entity
@Table(name = "professor_metrics")
public class ProfessorMetrics {

    @Id
    @Column(name = "professor_id", updatable = false)
    private UUID professorId;

    @Column(name = "rating_avg")
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "lessons_completed", nullable = false)
    private int lessonsCompleted;

    /** completadas / (completadas + ausencias confirmadas). Null si no hubo clases que medir. */
    @Column(name = "attendance_rate")
    private BigDecimal attendanceRate;

    @Column(name = "cancellation_rate")
    private BigDecimal cancellationRate;

    @Column(name = "reschedule_rate")
    private BigDecimal rescheduleRate;

    @Column(name = "response_rate")
    private BigDecimal responseRate;

    @Column(name = "avg_response_minutes")
    private Integer avgResponseMinutes;

    @Column(name = "active_students", nullable = false)
    private int activeStudents;

    @Column(name = "profile_completeness")
    private Short profileCompleteness;

    /** Lo que ordena el buscador. Null hasta que el job nocturno pase por primera vez. */
    @Column(name = "ranking_score")
    private BigDecimal rankingScore;

    @Column(name = "window_days", nullable = false)
    private short windowDays;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected ProfessorMetrics() {
        // exigido por JPA
    }

    public ProfessorMetrics(UUID professorId) {
        this.professorId = Objects.requireNonNull(professorId, "professorId");
        this.ratingCount = 0;
        this.windowDays = 90;
    }

    /** El agregado de reseñas, que se recalcula al instante y no espera al job nocturno. */
    public void recompute(BigDecimal ratingAvg, int ratingCount, Instant computedAt) {
        this.ratingAvg = ratingAvg;
        this.ratingCount = ratingCount;
        this.computedAt = Objects.requireNonNull(computedAt, "computedAt");
    }

    /** El resto del desempeño, que recalcula el job nocturno sobre la ventana móvil. */
    public void recomputePerformance(int lessonsCompleted,
                                     BigDecimal attendanceRate,
                                     BigDecimal cancellationRate,
                                     BigDecimal rescheduleRate,
                                     BigDecimal responseRate,
                                     Integer avgResponseMinutes,
                                     int activeStudents,
                                     Short profileCompleteness,
                                     BigDecimal rankingScore,
                                     short windowDays,
                                     Instant computedAt) {
        this.lessonsCompleted = lessonsCompleted;
        this.attendanceRate = attendanceRate;
        this.cancellationRate = cancellationRate;
        this.rescheduleRate = rescheduleRate;
        this.responseRate = responseRate;
        this.avgResponseMinutes = avgResponseMinutes;
        this.activeStudents = activeStudents;
        this.profileCompleteness = profileCompleteness;
        this.rankingScore = rankingScore;
        this.windowDays = windowDays;
        this.computedAt = Objects.requireNonNull(computedAt, "computedAt");
    }

    public int getLessonsCompleted() {
        return lessonsCompleted;
    }

    public BigDecimal getAttendanceRate() {
        return attendanceRate;
    }

    public BigDecimal getCancellationRate() {
        return cancellationRate;
    }

    public BigDecimal getRescheduleRate() {
        return rescheduleRate;
    }

    public BigDecimal getResponseRate() {
        return responseRate;
    }

    public Integer getAvgResponseMinutes() {
        return avgResponseMinutes;
    }

    public int getActiveStudents() {
        return activeStudents;
    }

    public Short getProfileCompleteness() {
        return profileCompleteness;
    }

    public BigDecimal getRankingScore() {
        return rankingScore;
    }

    public short getWindowDays() {
        return windowDays;
    }

    public UUID getProfessorId() {
        return professorId;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
