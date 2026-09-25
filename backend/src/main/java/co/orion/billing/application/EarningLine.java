package co.orion.billing.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Una clase en el desglose de ganancias: qué valió, cuánto se llevó Orión y cuánto queda.
 * {@code status} es el del pago, o {@code IN_TRANSIT} / {@code TRANSFERRED} si ya va en una
 * liquidación pendiente o pagada.
 */
public record EarningLine(UUID bookingId,
                          Instant classAt,
                          String studentName,
                          long amountCop,
                          long commissionCop,
                          long earningsCop,
                          String status) {
}
