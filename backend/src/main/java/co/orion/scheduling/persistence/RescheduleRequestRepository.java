package co.orion.scheduling.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.scheduling.domain.RescheduleRequest;
import co.orion.scheduling.domain.RescheduleStatus;

public interface RescheduleRequestRepository extends JpaRepository<RescheduleRequest, UUID> {

    Optional<RescheduleRequest> findByBookingIdAndStatus(UUID bookingId, RescheduleStatus status);

    List<RescheduleRequest> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Las propuestas vivas de las reservas de una persona, sean suyas o de su contraparte. Alimenta
     * el aviso de "tienes una propuesta por responder" sin pedir una consulta por reserva.
     */
    @Query("""
            select r from RescheduleRequest r, Booking b
            where b.id = r.bookingId
              and r.status = co.orion.scheduling.domain.RescheduleStatus.PENDING
              and (b.studentId = :userId or b.professorId = :userId)
            order by r.createdAt desc
            """)
    List<RescheduleRequest> findPendingOf(@Param("userId") UUID userId);

    /**
     * Cuántas reprogramaciones pidió el PROFESOR en la ventana. Solo las suyas: que un estudiante
     * mueva sus clases no dice nada del profesor.
     */
    @Query("""
            select count(r) from RescheduleRequest r, Booking b
            where b.id = r.bookingId
              and b.professorId = :professorId
              and r.requestedBy = :professorId
              and r.createdAt > :since
            """)
    long countRequestedByProfessorSince(@Param("professorId") UUID professorId,
                                        @Param("since") Instant since);

    /** Propuestas cuya clase original ya empezó: nadie respondió y la reserva siguió su curso. */
    @Query("""
            select r from RescheduleRequest r, Booking b
            where b.id = r.bookingId
              and r.status = co.orion.scheduling.domain.RescheduleStatus.PENDING
              and b.startsAt <= :now
            """)
    List<RescheduleRequest> findOverdue(@Param("now") Instant now);

    /** Propuestas de cambio esperando respuesta. */
    @Query("""
            select count(r) from RescheduleRequest r
            where r.status = co.orion.scheduling.domain.RescheduleStatus.PENDING
            """)
    long countPending();
}
