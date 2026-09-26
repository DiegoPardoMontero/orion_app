package co.orion.billing.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.billing.domain.PayoutAdjustment;

public interface PayoutAdjustmentRepository extends JpaRepository<PayoutAdjustment, UUID> {

    List<PayoutAdjustment> findByProfessorIdAndAppliedPayoutIdIsNullOrderByCreatedAtAsc(UUID professorId);

    @Query("select distinct a.professorId from PayoutAdjustment a where a.appliedPayoutId is null")
    List<UUID> findProfessorsWithPendingAdjustments();

    /** Al regenerar un borrador: sus ajustes vuelven a esperar. */
    @Modifying
    @Query("update PayoutAdjustment a set a.appliedPayoutId = null where a.appliedPayoutId = :payoutId")
    void releaseFrom(@Param("payoutId") UUID payoutId);

    boolean existsByBookingIdAndKind(UUID bookingId, co.orion.billing.domain.PayoutLineKind kind);
}
