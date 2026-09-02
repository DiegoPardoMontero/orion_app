package co.orion.billing.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.billing.domain.PaymentEvent;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}
