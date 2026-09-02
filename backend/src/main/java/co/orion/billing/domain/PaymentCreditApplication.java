package co.orion.billing.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cuánto crédito, y de qué fila exactamente, pagó una reserva. Existe para poder DESHACER: cuando
 * una reserva vence sin pagarse hay que devolverle a cada crédito lo suyo, conservando su motivo y
 * su vencimiento. Sin este detalle solo sabríamos el total, y devolver el total en una fila nueva
 * sería inventarle al estudiante un crédito que no tenía.
 */
@Entity
@Table(name = "payment_credit_applications")
@EntityListeners(AuditingEntityListener.class)
public class PaymentCreditApplication {

    @Id
    @Generated(event = EventType.INSERT)
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "credit_id", nullable = false, updatable = false)
    private UUID creditId;

    @Column(name = "amount_cop", nullable = false, updatable = false)
    private long amountCop;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentCreditApplication() {
        // exigido por JPA
    }

    public PaymentCreditApplication(UUID paymentId, UUID creditId, long amountCop) {
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.creditId = Objects.requireNonNull(creditId, "creditId");
        this.amountCop = amountCop;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getCreditId() {
        return creditId;
    }

    public long getAmountCop() {
        return amountCop;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
