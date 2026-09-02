package co.orion.billing.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Embeddable;

/** Clave compuesta de payout_items. */
@Embeddable
public class PayoutItemId implements Serializable {

    private UUID payoutId;
    private UUID paymentId;

    protected PayoutItemId() {
        // exigido por JPA
    }

    public PayoutItemId(UUID payoutId, UUID paymentId) {
        this.payoutId = payoutId;
        this.paymentId = paymentId;
    }

    public UUID getPayoutId() {
        return payoutId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PayoutItemId id
                && Objects.equals(payoutId, id.payoutId)
                && Objects.equals(paymentId, id.paymentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payoutId, paymentId);
    }
}
