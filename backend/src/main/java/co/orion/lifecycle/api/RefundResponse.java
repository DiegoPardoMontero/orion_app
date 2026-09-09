package co.orion.lifecycle.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.billing.domain.RefundRequest;

/** Una devolución debida. `daysLeft` es lo que ordena la cola del admin y lo que pinta el rojo. */
public record RefundResponse(UUID id, UUID bookingId, UUID studentId, String studentName,
                             String reason, long amountCop, String status, Instant dueAt,
                             long daysLeft, boolean overdue, Instant requestedAt,
                             Instant paidAt, String wompiReference) {

    public static RefundResponse from(RefundRequest r, String studentName, Instant now) {
        long dias = java.time.Duration.between(now, r.getDueAt()).toDays();
        return new RefundResponse(r.getId(), r.getBookingId(), r.getStudentId(), studentName,
                r.getReason().name(), r.getAmountCop(), r.getStatus().name(), r.getDueAt(),
                dias, r.isOverdue(now), r.getRequestedAt(), r.getPaidAt(), r.getWompiReference());
    }
}
