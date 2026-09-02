package co.orion.billing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * El hecho crudo que mandó la pasarela, guardado ANTES de interpretarlo. Si la lógica falla, el
 * evento no se pierde y se puede reprocesar; si la pasarela reenvía (lo hace, es normal), el
 * UNIQUE (provider, provider_event_id) lo rechaza y nada se procesa dos veces.
 *
 * Es una tabla de solo-inserción: no tiene setters ni transiciones a propósito.
 */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    /** Null si el evento llegó de una transacción que no corresponde a ningún pago nuestro. */
    @Column(name = "payment_id", updatable = false)
    private UUID paymentId;

    @Column(name = "provider", nullable = false, updatable = false, length = 20)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, updatable = false, length = 140)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 60)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Generated(event = EventType.INSERT)
    @ColumnDefault("now()")
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected PaymentEvent() {
        // exigido por JPA
    }

    public PaymentEvent(UUID paymentId,
                        String provider,
                        String providerEventId,
                        String eventType,
                        String payload) {
        this.paymentId = paymentId;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.providerEventId = Objects.requireNonNull(providerEventId, "providerEventId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
