package co.orion.lifecycle.application;

import java.util.UUID;

/** Un estudiante abrió un reclamo. Lo escuchan las notificaciones al admin y al profesor. */
public record DisputeOpened(UUID disputeId) {
}
