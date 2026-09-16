package co.orion.assessment.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un diagnóstico de confianza: la conversación, su resultado y lo que se dijo de ella.
 *
 * <p><strong>El puntaje no es un nivel.</strong> No equivale a A2, B1 ni a nada del MCER. Alguien
 * con gramática impecable y pánico escénico puntúa bajo, y eso es correcto: es justo lo que se
 * quiere medir. Tampoco mide comprensión, vocabulario ni pronunciación, y nunca se le muestra a
 * otro estudiante.
 *
 * <p>Las invariantes duras las guarda la base (V34): no existe una evaluación COMPLETED sin fecha
 * de fin ni sin puntaje, y no puede haber dos vivas por persona e idioma. Aquí se hace el chequeo
 * amable; el árbitro es la constraint.
 */
@Entity
@Table(name = "confidence_assessments")
public class ConfidenceAssessment {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "language_code", nullable = false, length = 5, updatable = false)
    private String languageCode;

    /** La cuántas veces. Es lo que sostiene la curva de progreso entre diagnósticos. */
    @Column(name = "sequence", nullable = false)
    private short sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssessmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private AssessmentMode mode;

    @Column(name = "score")
    private Short score;

    @Column(name = "score_version", length = 10)
    private String scoreVersion;

    /** Las cinco dimensiones normalizadas, tal como las calculó el `ConfidenceScoreCalculator`. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signals")
    private String signals;

    @Column(name = "summary", length = 600)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "observations")
    private String observations;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "turn_count")
    private Short turnCount;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ConfidenceAssessment() {
    }

    public ConfidenceAssessment(UUID userId, String languageCode, int sequence, Instant startedAt) {
        this.userId = userId;
        this.languageCode = languageCode;
        this.sequence = (short) sequence;
        this.status = AssessmentStatus.IN_PROGRESS;
        this.mode = AssessmentMode.STANDARD;
        this.startedAt = startedAt;
    }

    /** La conversación se fue a español: se cierra sin número, a propósito. */
    public void switchToFromZero() {
        this.mode = AssessmentMode.FROM_ZERO;
    }

    public void complete(Puntaje puntaje, String signalsJson, String observationsJson,
                         String summary, int durationSeconds, int turnCount, Instant now) {
        this.status = AssessmentStatus.COMPLETED;
        this.score = (short) puntaje.valor();
        this.scoreVersion = puntaje.version();
        this.signals = signalsJson;
        this.observations = observationsJson;
        this.summary = summary;
        this.durationSeconds = durationSeconds;
        this.turnCount = (short) turnCount;
        this.completedAt = now;
    }

    /**
     * Cierra sin resultado. Es lo que pasa cuando no hay turnos suficientes para sostener un número:
     * inventarlo sería peor que no darlo, porque un puntaje sacado de dos frases parece un dato.
     */
    public void abandon(int durationSeconds, int turnCount) {
        this.status = AssessmentStatus.ABANDONED;
        this.durationSeconds = durationSeconds;
        this.turnCount = (short) turnCount;
    }

    public void fail() {
        this.status = AssessmentStatus.FAILED;
    }

    public boolean isOwnedBy(UUID someone) {
        return userId.equals(someone);
    }

    public boolean isLive() {
        return status == AssessmentStatus.IN_PROGRESS;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public short getSequence() {
        return sequence;
    }

    public AssessmentStatus getStatus() {
        return status;
    }

    public AssessmentMode getMode() {
        return mode;
    }

    public Short getScore() {
        return score;
    }

    public String getScoreVersion() {
        return scoreVersion;
    }

    public String getSignals() {
        return signals;
    }

    public String getSummary() {
        return summary;
    }

    public String getObservations() {
        return observations;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Short getTurnCount() {
        return turnCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

}
