package co.orion.billing.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import co.orion.billing.domain.Payment;
import co.orion.billing.domain.PaymentStatus;

/**
 * Filtros de la pantalla de conciliación. Specification y no un @Query con "(:x is null or ...)"
 * por la misma razón que en BookingRepository: Postgres no infiere el tipo de un parámetro nulo y
 * respondería "could not determine data type of parameter $1". Aquí el filtro que no llegó
 * sencillamente no se añade a la consulta.
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    public static Specification<Payment> status(PaymentStatus status) {
        return status == null ? null
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Payment> professor(UUID professorId) {
        return professorId == null ? null
                : (root, query, cb) -> cb.equal(root.get("professorId"), professorId);
    }

    public static Specification<Payment> student(UUID studentId) {
        return studentId == null ? null
                : (root, query, cb) -> cb.equal(root.get("studentId"), studentId);
    }

    public static Specification<Payment> createdFrom(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Payment> createdBefore(Instant to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }
}
