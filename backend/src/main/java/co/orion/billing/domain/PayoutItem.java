package co.orion.billing.domain;

import java.util.UUID;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Qué pagos entraron en qué liquidación. payment_id es UNIQUE en toda la tabla, no solo dentro de
 * un payout: una clase se le paga al profesor UNA vez, y de eso responde la base.
 */
@Entity
@Table(name = "payout_items")
public class PayoutItem {

    @EmbeddedId
    private PayoutItemId id;

    protected PayoutItem() {
        // exigido por JPA
    }

    public PayoutItem(UUID payoutId, UUID paymentId) {
        this.id = new PayoutItemId(payoutId, paymentId);
    }

    public PayoutItemId getId() {
        return id;
    }

    public UUID getPayoutId() {
        return id.getPayoutId();
    }

    public UUID getPaymentId() {
        return id.getPaymentId();
    }
}
