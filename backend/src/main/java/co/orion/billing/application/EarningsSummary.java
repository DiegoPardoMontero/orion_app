package co.orion.billing.application;

import java.util.List;

/**
 * Las ganancias de un profesor en un período, en los tres estados que de verdad le importan:
 * lo que está retenido (la clase aún no se dio), lo que ya se ganó pero no se le ha transferido, y
 * lo que ya está en su cuenta.
 */
public record EarningsSummary(long heldCop,
                              long payableCop,
                              long transferredCop,
                              List<EarningLine> lines) {

    public long totalCop() {
        return heldCop + payableCop + transferredCop;
    }
}
