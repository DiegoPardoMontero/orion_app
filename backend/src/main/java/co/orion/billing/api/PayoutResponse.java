package co.orion.billing.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import co.orion.billing.domain.Payout;

public record PayoutResponse(UUID id,
                             UUID professorId,
                             String professorName,
                             LocalDate periodStart,
                             LocalDate periodEnd,
                             long amountCop,
                             String status,
                             String reference,
                             Instant paidAt,
                             Instant createdAt) {

    public static PayoutResponse of(Payout payout, String professorName) {
        return new PayoutResponse(
                payout.getId(),
                payout.getProfessorId(),
                professorName,
                payout.getPeriodStart(),
                payout.getPeriodEnd(),
                payout.getAmountCop(),
                payout.getStatus().name(),
                payout.getReference(),
                payout.getPaidAt(),
                payout.getCreatedAt());
    }
}
