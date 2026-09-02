package co.orion.billing.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.billing.domain.Payout;
import co.orion.billing.domain.PayoutStatus;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    List<Payout> findAllByOrderByCreatedAtDesc();

    List<Payout> findByProfessorIdOrderByCreatedAtDesc(UUID professorId);

    List<Payout> findByStatusOrderByCreatedAtDesc(PayoutStatus status);
}
