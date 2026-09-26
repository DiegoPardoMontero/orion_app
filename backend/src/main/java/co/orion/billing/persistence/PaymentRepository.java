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
              and exists (select 1 from PayoutLine l, Payout o
                          where l.paymentId = p.id
                            and o.id = l.payoutId
                            and o.status = co.orion.billing.domain.PayoutStatus.PAID)
            """)
    long sumAlreadyTransferred(@Param("professorId") UUID professorId,
                               @Param("from") Instant from,
                               @Param("to") Instant to);

    /**
     * Lo que ya viaja en una liquidación pero todavía no ha salido: la transferencia la hace una
     * persona y el banco tarda. Antes este dinero se sumaba a "por cobrar", y eso le decía al
     * profesor que su pago entraría en la próxima liquidación cuando ya estaba en una.
     */
    @Query("""
            select coalesce(sum(p.professorEarningsCop), 0) from Payment p
            where p.professorId = :professorId
              and p.createdAt >= :from
              and p.createdAt < :to
              and exists (select 1 from PayoutLine l, Payout o
                          where l.paymentId = p.id
                            and o.id = l.payoutId
                            and o.status <> co.orion.billing.domain.PayoutStatus.PAID)
            """)
    long sumInTransit(@Param("professorId") UUID professorId,
                      @Param("from") Instant from,
                      @Param("to") Instant to);

    /* --- Cifras del tablero del admin --- */

    @Query("""
            select coalesce(sum(p.professorEarningsCop), 0) from Payment p where p.status = :status
            """)
    long sumEarningsByStatusAllProfessors(@Param("status") PaymentStatus status);

    /**
     * Lo que los profesores ya se ganaron y Orión todavía no les transfirió: liberado y fuera de
     * toda liquidación pagada. Un pago sigue RELEASED después de transferido —la liquidación es la
     * que cambia—, así que sumar solo por estado contaba dos veces lo ya pagado.
     */
    @Query("""
            select coalesce(sum(p.professorEarningsCop), 0) from Payment p
            where p.status = co.orion.billing.domain.PaymentStatus.RELEASED
              and not exists (select 1 from PayoutLine l, Payout o
                              where l.paymentId = p.id and o.id = l.payoutId
                                and o.status = co.orion.billing.domain.PayoutStatus.PAID)
            """)
    long sumPayableAllProfessors();

    /** De estos pagos, los que van en una liquidación con alguno de esos estados. */
    @Query("""
            select l.paymentId from PayoutLine l, Payout o
             where o.id = l.payoutId and o.status in :statuses and l.paymentId in :paymentIds
            """)
    List<UUID> findInPayoutsWithStatus(@Param("paymentIds") java.util.Collection<UUID> paymentIds,
                                      @Param("statuses") java.util.Collection<co.orion.billing.domain.PayoutStatus> statuses);

    /** Lo que ya salió hacia las cuentas de los profesores. */
    @Query("""
            select coalesce(sum(p.professorEarningsCop), 0) from Payment p
            where exists (select 1 from PayoutLine l, Payout o
                          where l.paymentId = p.id and o.id = l.payoutId
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
