package co.orion.billing.api;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.billing.domain.Payment;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BusinessZone;

/**
 * Una línea del historial de pagos del estudiante. Lleva el precio y lo que puso de su bolsillo,
 * nunca la comisión: el estudiante compra una clase, no un servicio de intermediación.
 */
public record MyPaymentResponse(UUID paymentId,
                                UUID bookingId,
                                ZonedDateTime classAt,
                                String professorName,
                                long amountCop,
                                long creditAppliedCop,
                                long chargedCop,
                                String status,
                                Instant paidAt) {

    public static MyPaymentResponse of(Payment payment, Booking booking, String professorName) {
        return new MyPaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                booking != null ? booking.getStartsAt().atZone(BusinessZone.BOGOTA) : null,
                professorName,
                payment.getAmountCop(),
                payment.getCreditAppliedCop(),
                payment.getChargedCop(),
                payment.getStatus().name(),
                payment.getPaidAt());
    }
}
