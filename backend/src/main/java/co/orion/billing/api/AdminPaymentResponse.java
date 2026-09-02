package co.orion.billing.api;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.billing.domain.Payment;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BusinessZone;

/**
 * La vista del admin: todo, incluida la comisión y el estado de la clase. {@code needsReview} marca
 * los casos que una persona tiene que mirar —hoy, el que más importa: una clase cancelada por el
 * estudiante cuyo pago ya había entrado. Ese dinero no se le paga al profesor (la clase nunca
 * llega a COMPLETED) y tampoco vuelve solo: alguien decide entre saldo o devolución por Wompi.
 */
public record AdminPaymentResponse(UUID paymentId,
                                   UUID bookingId,
                                   ZonedDateTime classAt,
                                   String bookingStatus,
                                   UUID studentId,
                                   String studentName,
                                   UUID professorId,
                                   String professorName,
                                   long amountCop,
                                   long creditAppliedCop,
                                   long chargedCop,
                                   int commissionRateBps,
                                   long commissionCop,
                                   long professorEarningsCop,
                                   String status,
                                   String provider,
                                   String providerReference,
                                   Instant paidAt,
                                   Instant releasedAt,
                                   boolean needsReview) {

    public static AdminPaymentResponse of(Payment payment,
                                          Booking booking,
                                          String studentName,
                                          String professorName) {
        boolean cancelledButPaid = payment.isPaid()
                && booking != null
                && booking.getStatus().isTerminal();

        return new AdminPaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                booking != null ? booking.getStartsAt().atZone(BusinessZone.BOGOTA) : null,
                booking != null ? booking.getStatus().name() : null,
                payment.getStudentId(),
                studentName,
                payment.getProfessorId(),
                professorName,
                payment.getAmountCop(),
                payment.getCreditAppliedCop(),
                payment.getChargedCop(),
                payment.getCommissionRateBps(),
                payment.getCommissionCop(),
                payment.getProfessorEarningsCop(),
                payment.getStatus().name(),
                payment.getProvider(),
                payment.getProviderReference(),
                payment.getPaidAt(),
                payment.getReleasedAt(),
                cancelledButPaid);
    }
}
