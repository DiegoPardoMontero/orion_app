package co.orion.catalog.domain;

/**
 * Desglose de una tarifa: cuánto cobra el estudiante, cuánto retiene Orión y cuánto recibe el
 * profesor. Cálculo puro con enteros (pesos colombianos), redondeo de la comisión HACIA ABAJO al
 * peso — la diferencia queda a favor del profesor. Sin Spring, exhaustivamente testeable.
 */
public record RateBreakdown(long hourlyRateCop, int commissionRateBps, long commissionCop, long earningsCop) {

    public static RateBreakdown of(long hourlyRateCop, int commissionRateBps) {
        if (hourlyRateCop < 0) {
            throw new IllegalArgumentException("La tarifa no puede ser negativa");
        }
        if (commissionRateBps < 0 || commissionRateBps > 10000) {
            throw new IllegalArgumentException("La comisión debe estar entre 0 y 10000 bps");
        }
        long commission = hourlyRateCop * commissionRateBps / 10000; // redondeo hacia abajo
        return new RateBreakdown(hourlyRateCop, commissionRateBps, commission, hourlyRateCop - commission);
    }
}
