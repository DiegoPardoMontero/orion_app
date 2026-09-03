package co.orion.scheduling.api;

import java.util.UUID;

import co.orion.scheduling.application.PaymentTicket;

/**
 * Lo que hay que pagar por una reserva recién creada. {@code checkoutUrl} es null cuando no hay nada
 * que cobrar —el crédito cubrió la clase entera, o la clase es gratuita—: en ese caso la reserva ya
 * nace confirmada y no hay a dónde ir a pagar.
 *
 * Sin comisión: este objeto viaja al estudiante, y cuánto retiene Orión no es asunto suyo.
 */
public record PaymentTicketResponse(UUID paymentId,
                                    long amountCop,
                                    long creditAppliedCop,
                                    long chargedCop,
                                    String checkoutUrl) {

    public static PaymentTicketResponse from(PaymentTicket ticket) {
        return new PaymentTicketResponse(
                ticket.paymentId(),
                ticket.amountCop(),
                ticket.creditAppliedCop(),
                ticket.chargedCop(),
                ticket.checkoutUrl());
    }
}
