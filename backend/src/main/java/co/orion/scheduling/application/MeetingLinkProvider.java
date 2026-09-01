package co.orion.scheduling.application;

import java.util.UUID;

/**
 * Genera la sala de videollamada de una reserva virtual. Detrás de interfaz a propósito: en el
 * MVP 2 un {@code GoogleMeetProvider} vía Calendar API reemplaza esta implementación sin tocar el
 * resto del dominio.
 */
public interface MeetingLinkProvider {

    String linkFor(UUID bookingId);
}
