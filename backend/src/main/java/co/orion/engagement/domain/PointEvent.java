package co.orion.engagement.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un hecho que concedió puntos. El libro es append-only: no se edita ni se borra, se añade.
 *
 * <p>La idempotencia no vive aquí sino en el índice único {@code (source_type, source_id)}: un
 * hecho concede puntos una sola vez, para siempre. Es lo que permite reprocesar un evento reenviado
 * o recalcular a un estudiante desde cero sin duplicar nada, y sin un solo {@code if}.
 */
@Entity
@Table(name = "point_events")
public class PointEvent {

    @Id
    @org.hibernate.annotations.Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "source_type", nullable = false, length = 40, updatable = false)
    private String sourceType;

    /** Qué hecho concreto. Null solo para fuentes que no tienen entidad detrás. */
    @Column(name = "source_id", updatable = false)
    private UUID sourceId;

    @Column(name = "points", nullable = false, updatable = false)
    private int points;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected PointEvent() {
        // exigido por JPA
    }

    public PointEvent(UUID userId, String sourceType, UUID sourceId, int points, Instant occurredAt) {
        if (points <= 0) {
            throw new IllegalArgumentException("Un evento de puntos suma; no resta ni deja igual");
        }
        this.userId = Objects.requireNonNull(userId, "userId");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.sourceId = sourceId;
        this.points = points;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public int getPoints() {
        return points;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
