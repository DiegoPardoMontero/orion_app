package co.orion.scheduling.application;

import java.util.UUID;

/**
 * Lo que hay que saber para pagar una reserva recién creada: cuánto vale la clase, cuánto se cubrió
 * con crédito, cuánto queda por cobrar y a dónde ir a pagarlo.
 *
 * {@code checkoutUrl} es null cuando no hay nada que cobrar —el crédito cubrió la clase entera, o
 * la clase es gratuita—: en ese caso la reserva ya nace confirmada y no hay pasarela de por medio.
 *
 * Deliberadamente NO lleva la comisión: este objeto viaja al estudiante, y cuánto retiene Orión no
 * es asunto suyo.
 */
public record PaymentTicket(UUID paymentId,
                            long amountCop,
                            long creditAppliedCop,
                            long chargedCop,
                            String checkoutUrl) {

    public boolean nothingToCharge() {
        return chargedCop == 0;
    }
}
