package co.orion.billing.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import co.orion.billing.application.EarningLine;
import co.orion.billing.application.EarningsSummary;
import co.orion.scheduling.domain.BusinessZone;

/**
 * "Mis ganancias" del profesor. Aquí la comisión SÍ se muestra, y con detalle por clase: el
 * profesor tiene derecho a ver exactamente qué se le descontó y sobre qué base.
 */
public record EarningsResponse(long heldCop,
                               long payableCop,
                               long transferredCop,
                               long totalCop,
                               List<Line> lines) {

    public record Line(UUID bookingId,
                       ZonedDateTime classAt,
                       String studentName,
                       long amountCop,
                       long commissionCop,
                       long earningsCop,
                       String status) {
    }

    public static EarningsResponse from(EarningsSummary summary) {
        return new EarningsResponse(
                summary.heldCop(),
                summary.payableCop(),
                summary.transferredCop(),
                summary.totalCop(),
                summary.lines().stream().map(EarningsResponse::line).toList());
    }

    private static Line line(EarningLine source) {
        return new Line(
                source.bookingId(),
                source.classAt() != null ? source.classAt().atZone(BusinessZone.BOGOTA) : null,
                source.studentName(),
                source.amountCop(),
                source.commissionCop(),
                source.earningsCop(),
                source.status());
    }
}
