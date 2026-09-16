package co.orion.billing.application;

import java.util.List;

/**
 * Las ganancias de un profesor en un período, en los cuatro estados por los que pasa su dinero:
 * retenido (la clase aún no se dio), por cobrar (dictada, sin liquidar), en camino (liquidada, sin
 * pagar) y transferido (en su cuenta).
 *
 * <p>«En camino» se separó de «por cobrar» porque juntos mentían: a quien ya tenía su pago dentro
 * de una liquidación se le seguía diciendo que entraría en la próxima.
 */
public record EarningsSummary(long heldCop,
                              long payableCop,
                              long inTransitCop,
                              long transferredCop,
                              List<EarningLine> lines) {

    public long totalCop() {
        return heldCop + payableCop + inTransitCop + transferredCop;
    }
}
