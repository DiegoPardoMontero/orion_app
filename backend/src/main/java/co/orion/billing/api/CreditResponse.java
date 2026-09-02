package co.orion.billing.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.billing.domain.StudentCredit;

public record CreditResponse(UUID id,
                             long amountCop,
                             long remainingCop,
                             String reason,
                             UUID bookingId,
                             Instant expiresAt,
                             Instant createdAt) {

    public static CreditResponse from(StudentCredit credit) {
        return new CreditResponse(
                credit.getId(),
                credit.getAmountCop(),
                credit.getRemainingCop(),
                credit.getReason().name(),
                credit.getBookingId(),
                credit.getExpiresAt(),
                credit.getCreatedAt());
    }
}
