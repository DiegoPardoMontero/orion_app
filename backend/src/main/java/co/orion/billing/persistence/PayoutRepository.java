package co.orion.billing.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.billing.domain.Payout;
import co.orion.billing.domain.PayoutStatus;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    List<Payout> findAllByOrderByCreatedAtDesc();

    List<Payout> findByProfessorIdOrderByPeriodStartDesc(UUID professorId);

    List<Payout> findByPeriodStartOrderByCreatedAtAsc(LocalDate periodStart);

    List<Payout> findByStatusIn(Collection<PayoutStatus> statuses);

    boolean existsByProfessorIdAndPeriodStart(UUID professorId, LocalDate periodStart);
}
