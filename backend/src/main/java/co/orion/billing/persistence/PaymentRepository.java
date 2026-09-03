package co.orion.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.orion.billing.domain.Payment;
import co.orion.billing.domain.PaymentStatus;

/**
 * JpaSpecificationExecutor alimenta la conciliación del admin con filtros opcionales, por la misma
 * razón que en BookingRepository: Postgres no infiere el tipo de un parámetro nulo.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID>,
        JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByBookingId(UUID bookingId);

    List<Payment> findByBookingIdIn(java.util.Collection<UUID> bookingIds);

    Optional<Payment> findByProviderAndProviderReference(String provider, String providerReference);

    List<Payment> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<Payment> findByProfessorIdAndStatus(UUID professorId, PaymentStatus status);

    /**
     * Los pagos liberados de un profesor que aún no están en ninguna liquidación, dentro del
     * período. El NOT EXISTS —y no un LEFT JOIN— porque payout_items no tiene entidad propia
     * relacionada: el UNIQUE de la tabla es quien impide de verdad pagar dos veces.
     */
    @Query("""
            select p from Payment p
            where p.professorId = :professorId
              and p.status = co.orion.billing.domain.PaymentStatus.RELEASED
              and p.releasedAt >= :from
              and p.releasedAt < :to
              and not exists (select i from PayoutItem i where i.id.paymentId = p.id)
            order by p.releasedAt asc
            """)
    List<Payment> findPayableOfProfessor(@Param("professorId") UUID professorId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    /** Los profesores con algo que cobrar en el período: la entrada del generador de liquidaciones. */
    @Query("""
            select distinct p.professorId from Payment p
            where p.status = co.orion.billing.domain.PaymentStatus.RELEASED
              and p.releasedAt >= :from
              and p.releasedAt < :to
              and not exists (select i from PayoutItem i where i.id.paymentId = p.id)
            """)
    List<UUID> findProfessorsWithPayableEarnings(@Param("from") Instant from,
                                                 @Param("to") Instant to);

    @Query("""
            select coalesce(sum(p.professorEarningsCop), 0) from Payment p
            where p.professorId = :professorId
              and p.status = :status
              and p.createdAt >= :from
              and p.createdAt < :to
            """)
    long sumEarningsByStatus(@Param("professorId") UUID professorId,
                             @Param("status") PaymentStatus status,
                             @Param("from") Instant from,
                             @Param("to") Instant to);

    List<Payment> findByProfessorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID professorId, Instant from, Instant to);

    /**
     * Lo que ya se le transfirió al profesor: pagos liberados que además viajan en una liquidación
     * marcada como pagada. Un pago liberado y todavía sin liquidar sigue contando como "por cobrar".
     */
    @Query("""
            select coalesce(sum(p.professorEarningsCop), 0) from Payment p
            where p.professorId = :professorId
              and p.createdAt >= :from
              and p.createdAt < :to
              and exists (select 1 from PayoutItem i, Payout o
                          where i.id.paymentId = p.id
                            and o.id = i.id.payoutId
                            and o.status = co.orion.billing.domain.PayoutStatus.PAID)
            """)
    long sumAlreadyTransferred(@Param("professorId") UUID professorId,
                               @Param("from") Instant from,
                               @Param("to") Instant to);

    /* --- Cifras del tablero del admin --- */

    @Query("""
            select coalesce(sum(p.professorEarningsCop), 0) from Payment p where p.status = :status
            """)
    long sumEarningsByStatusAllProfessors(@Param("status") PaymentStatus status);

    /** Lo que ya salió hacia las cuentas de los profesores. */
    @Query("""
            select coalesce(sum(p.professorEarningsCop), 0) from Payment p
            where exists (select 1 from PayoutItem i, Payout o
                          where i.id.paymentId = p.id and o.id = i.id.payoutId
                            and o.status = co.orion.billing.domain.PayoutStatus.PAID)
            """)
    long sumTransferred();

    /** Lo que Orión se ha ganado de comisión sobre clases que de verdad se cobraron. */
    @Query("""
            select coalesce(sum(p.commissionCop), 0) from Payment p where p.status in :statuses
            """)
    long sumCommissionOn(@Param("statuses") java.util.Collection<PaymentStatus> statuses);

    /** Pagos que esperan una decisión: cobrados sin clase, o marcados por un incidente. */
    @Query("""
            select count(p) from Payment p
            where p.status = co.orion.billing.domain.PaymentStatus.DISPUTED
               or (p.status = co.orion.billing.domain.PaymentStatus.PAID
                   and exists (select 1 from Booking b where b.id = p.bookingId
                               and b.status in (co.orion.scheduling.domain.BookingStatus.CANCELLED_BY_STUDENT,
                                                co.orion.scheduling.domain.BookingStatus.CANCELLED_BY_PROFESSOR,
                                                co.orion.scheduling.domain.BookingStatus.CANCELLED_BY_ADMIN,
                                                co.orion.scheduling.domain.BookingStatus.EXPIRED)))
            """)
    long countNeedingReview();
}
