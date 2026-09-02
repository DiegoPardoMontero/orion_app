package co.orion.billing.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.billing.domain.PaymentCreditApplication;

public interface PaymentCreditApplicationRepository
        extends JpaRepository<PaymentCreditApplication, UUID> {

    List<PaymentCreditApplication> findByPaymentId(UUID paymentId);
}
