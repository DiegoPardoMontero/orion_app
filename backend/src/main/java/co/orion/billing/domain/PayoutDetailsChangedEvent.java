package co.orion.billing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Cambiaron los datos de pago de un profe. Se le avisa por correo cada vez, con la llave enmascarada:
 * si no fue él, es la señal de que alguien quiere desviar sus pagos.
 */
public record PayoutDetailsChangedEvent(UUID professorId, String keyTypeLabel, String maskedKey, String holderName,
                                        Instant changedAt, boolean firstTime) {
}
