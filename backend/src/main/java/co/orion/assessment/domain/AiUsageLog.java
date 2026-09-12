package co.orion.assessment.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una llamada a la IA, con lo que costó.
 *
 * <p>Existe porque una función de costo desconocido no se puede sostener ni apagar a tiempo. Se
 * escribe una fila <strong>siempre</strong>: también cuando el proveedor se cayó, también cuando la
 * persona abandonó a los noventa segundos. Un registro que solo anota los éxitos miente sobre el
 * gasto y sobre la fiabilidad a la vez.
 */
@Entity
@Table(name = "ai_usage_log")
public class AiUsageLog {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    /** Qué parte del producto gastó. Hoy solo el diagnóstico; mañana habrá más. */
    @Column(name = "feature", nullable = false, length = 40, updatable = false)
    private String feature;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "provider", nullable = false, length = 40, updatable = false)
    private String provider;

    @Column(name = "model", length = 80, updatable = false)
    private String model;

    @Column(name = "voice_seconds", updatable = false)
    private Integer voiceSeconds;

    @Column(name = "input_tokens", updatable = false)
    private Integer inputTokens;

    @Column(name = "output_tokens", updatable = false)
    private Integer outputTokens;

    @Column(name = "cost_cop", updatable = false)
    private Long costCop;

    @Column(name = "latency_ms", updatable = false)
    private Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20, updatable = false)
    private AiUsageOutcome outcome;

    @Column(name = "occurred_at", nullable = false, updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant occurredAt;

    protected AiUsageLog() {
        // exigido por JPA
    }

    private AiUsageLog(String feature, UUID actorId, String provider, String model,
                       Integer voiceSeconds, Integer inputTokens, Integer outputTokens,
                       Long costCop, Integer latencyMs, AiUsageOutcome outcome) {
        this.feature = feature;
        this.actorId = actorId;
        this.provider = provider;
        this.model = model;
        this.voiceSeconds = voiceSeconds;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costCop = costCop;
        this.latencyMs = latencyMs;
        this.outcome = outcome;
    }

    /** Una conversación por voz que llegó a ocurrir, con lo que duró y lo que se estima que costó. */
    public static AiUsageLog voice(String feature, UUID actorId, String provider, String model,
                                   int voiceSeconds, long costCop, AiUsageOutcome outcome) {
        return new AiUsageLog(feature, actorId, provider, model, voiceSeconds,
                null, null, costCop, null, outcome);
    }

    /** Un intento que no llegó a conversación: el proveedor falló al abrir la sesión. */
    public static AiUsageLog failedAttempt(String feature, UUID actorId, String provider,
                                           String model, int latencyMs, AiUsageOutcome outcome) {
        return new AiUsageLog(feature, actorId, provider, model, null, null, null,
                0L, latencyMs, outcome);
    }

    public UUID getId() {
        return id;
    }

    public String getFeature() {
        return feature;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Integer getVoiceSeconds() {
        return voiceSeconds;
    }

    public Long getCostCop() {
        return costCop;
    }

    public AiUsageOutcome getOutcome() {
        return outcome;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
