package co.orion.assessment.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un turno de la conversación, con sus señales.
 *
 * <p><strong>Quién mide qué.</strong> La latencia y la duración las mide el cliente, que es el único
 * que ve el audio. Las demás —autocorrecciones, frases abandonadas, muletillas, cambio de idioma—
 * las deduce el servidor de la transcripción, porque son análisis de texto y no medición, y porque
 * un puntaje que depende de lo que diga el navegador no es reproducible ni defendible.
 *
 * <p>La transcripción se borra a los {@code assessment_transcript_retention_days}. Sobrevive el
 * puntaje, sobreviven las señales; el texto de lo que la persona dijo, no.
 */
@Entity
@Table(name = "assessment_turns")
public class AssessmentTurn {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private UUID assessmentId;

    @Column(name = "turn_index", nullable = false, updatable = false)
    private short turnIndex;

    @Column(name = "speaker", nullable = false, length = 10, updatable = false)
    private String speaker;

    @Column(name = "transcript", length = 2000)
    private String transcript;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "word_count")
    private Short wordCount;

    @Column(name = "self_corrections")
    private Short selfCorrections;

    @Column(name = "abandoned_clauses")
    private Short abandonedClauses;

    @Column(name = "filler_count")
    private Short fillerCount;

    @Column(name = "native_switch", nullable = false)
    private boolean nativeSwitch;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssessmentTurn() {
    }

    public AssessmentTurn(UUID assessmentId, int turnIndex, String speaker, String transcript,
                          Integer latencyMs, Integer durationMs, Instant createdAt) {
        this.assessmentId = assessmentId;
        this.turnIndex = (short) turnIndex;
        this.speaker = speaker;
        this.transcript = transcript;
        this.latencyMs = latencyMs;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }

    /** Las señales que deduce el servidor del texto. Nunca llegan del cliente. */
    public void withSignals(int wordCount, int selfCorrections, int abandonedClauses,
                            int fillerCount, boolean nativeSwitch) {
        this.wordCount = (short) wordCount;
        this.selfCorrections = (short) selfCorrections;
        this.abandonedClauses = (short) abandonedClauses;
        this.fillerCount = (short) fillerCount;
        this.nativeSwitch = nativeSwitch;
    }

    public boolean isUser() {
        return "USER".equals(speaker);
    }

    public TurnoDelUsuario asSignal() {
        return new TurnoDelUsuario(
                latencyMs == null ? 0 : latencyMs,
                wordCount == null ? 0 : wordCount,
                selfCorrections == null ? 0 : selfCorrections,
                abandonedClauses == null ? 0 : abandonedClauses,
                fillerCount == null ? 0 : fillerCount,
                nativeSwitch);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public short getTurnIndex() {
        return turnIndex;
    }

    public String getSpeaker() {
        return speaker;
    }

    public String getTranscript() {
        return transcript;
    }

    public boolean isNativeSwitch() {
        return nativeSwitch;
    }

    public Integer getDurationMs() {
        return durationMs;
    }
}
