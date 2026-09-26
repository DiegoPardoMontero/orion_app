package co.orion.billing.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.billing.domain.PayoutLine;

public interface PayoutLineRepository extends JpaRepository<PayoutLine, UUID> {

    List<PayoutLine> findByPayoutIdOrderByClassAtAscDescriptionAsc(UUID payoutId);

    List<PayoutLine> findByPayoutIdIn(List<UUID> payoutIds);

    java.util.Optional<PayoutLine> findFirstByBookingIdAndKindIn(UUID bookingId,
                                                                 List<co.orion.billing.domain.PayoutLineKind> kinds);

    @Modifying
    @Query("delete from PayoutLine l where l.payoutId = :payoutId")
    void deleteByPayoutId(@Param("payoutId") UUID payoutId);
}
