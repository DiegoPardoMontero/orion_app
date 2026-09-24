package co.orion.billing.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Orión le pagó al profesor una liquidación. Es el aviso que más espera. */
public record PayoutPaidEvent(UUID payoutId, UUID professorId, long amountCop, LocalDate periodStart,
                              LocalDate periodEnd) {
}
