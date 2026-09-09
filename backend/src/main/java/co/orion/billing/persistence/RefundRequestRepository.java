package co.orion.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import co.orion.billing.domain.RefundRequest;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    Optional<RefundRequest> findByBookingId(UUID bookingId);

    boolean existsByBookingId(UUID bookingId);

    List<RefundRequest> findByStudentIdOrderByRequestedAtDesc(UUID studentId);

    /** La cola del admin: lo que vence antes, primero. */
    List<RefundRequest> findByStatusOrderByDueAtAsc(RefundRequest.Status status);

    long countByStatus(RefundRequest.Status status);

    /** Las que vencen dentro de poco. Alimentan la alerta por correo. */
    List<RefundRequest> findByStatusAndDueAtBefore(RefundRequest.Status status, Instant limite);
}
