package co.orion.billing.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.billing.domain.PayoutCut;

public interface PayoutCutRepository extends JpaRepository<PayoutCut, LocalDate> {

    List<PayoutCut> findAllByOrderByPeriodStartDesc();
}
