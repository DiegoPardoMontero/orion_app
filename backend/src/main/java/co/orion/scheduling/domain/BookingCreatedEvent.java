package co.orion.scheduling.domain;

import java.util.UUID;

/**
 * Se publica al crear una reserva. Nadie lo escucha todavía: el listener de correos llega en el
 * Paso 5. Publicarlo desde ya mantiene el servicio de reservas ignorante de las notificaciones —
 * cuando existan, no habrá que tocar una línea de este módulo.
 */
public record BookingCreatedEvent(UUID bookingId) {
}
