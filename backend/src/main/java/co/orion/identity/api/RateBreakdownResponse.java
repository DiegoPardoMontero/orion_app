package co.orion.identity.api;

import co.orion.catalog.domain.RateBreakdown;

/** Desglose de tarifa que ve el profesor: cuánto cobra, cuánto retiene Orión, cuánto recibe. */
public record RateBreakdownResponse(long hourlyRateCop, int commissionRateBps, long commissionCop, long earningsCop) {

    public static RateBreakdownResponse from(RateBreakdown b) {
        return new RateBreakdownResponse(b.hourlyRateCop(), b.commissionRateBps(), b.commissionCop(), b.earningsCop());
    }
}
