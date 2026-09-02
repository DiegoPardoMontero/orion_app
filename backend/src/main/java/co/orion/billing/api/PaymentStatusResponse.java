package co.orion.billing.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.billing.domain.Payment;
import co.orion.scheduling.domain.Booking;

/**
 * El estado de un pago, para la pantalla de retorno de la pasarela. El frontend consulta esto
 * hasta que deje de estar PENDING —PSE puede tardar minutos— así que lleva también el estado de la
 * reserva: lo que el estudiante quiere saber es si su clase existe.
 *
 * {@code checkoutUrl} solo viene mientras el pago siga pendiente: es la forma de volver a la
 * pasarela sin perder el cupo. En cualquier otro estado es null.
 *
 * Sin {@code commissionCop}: ni aquí ni en ninguna respuesta al estudiante.
 */
public record PaymentStatusResponse(UUID bookingId,
                                    String bookingStatus,
                                    String paymentStatus,
                                    long amountCop,
                                    long creditAppliedCop,
                                    long chargedCop,
                                    Instant paidAt,
                                    Instant expiresAt,
                                    String checkoutUrl) {

    public static PaymentStatusResponse of(Booking booking, Payment payment, String checkoutUrl) {
        return new PaymentStatusResponse(
                booking.getId(),
                booking.getStatus().name(),
                payment.getStatus().name(),
                payment.getAmountCop(),
                payment.getCreditAppliedCop(),
                payment.getChargedCop(),
                payment.getPaidAt(),
                booking.getExpiresAt(),
                checkoutUrl);
    }
}
