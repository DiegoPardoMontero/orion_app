package co.orion.billing.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.billing.domain.PayoutItem;
import co.orion.billing.domain.PayoutItemId;

public interface PayoutItemRepository extends JpaRepository<PayoutItem, PayoutItemId> {

    List<PayoutItem> findByIdPayoutId(UUID payoutId);
}
