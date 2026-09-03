package co.orion.lifecycle.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.lifecycle.domain.Dispute;
import co.orion.lifecycle.domain.DisputeStatus;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    List<Dispute> findByStatusOrderByCreatedAtDesc(DisputeStatus status);

    List<Dispute> findByStatusInOrderByCreatedAtDesc(Collection<DisputeStatus> statuses);

    List<Dispute> findAllByOrderByCreatedAtDesc();

    Optional<Dispute> findByBookingIdAndStatusIn(UUID bookingId, Collection<DisputeStatus> statuses);

    List<Dispute> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /** Los ids de reserva con reclamo vivo: el filtro que el autocompletado no puede saltarse. */
    @org.springframework.data.jpa.repository.Query("""
            select d.bookingId from Dispute d
            where d.status in (co.orion.lifecycle.domain.DisputeStatus.OPEN,
                               co.orion.lifecycle.domain.DisputeStatus.UNDER_REVIEW)
            """)
    List<UUID> findBookingIdsWithOpenDispute();
}
