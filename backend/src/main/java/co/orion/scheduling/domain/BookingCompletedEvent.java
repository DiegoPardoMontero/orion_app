package co.orion.scheduling.domain;

import java.util.UUID;

/**
 * La clase ocurrió y el profesor registró el resultado. {@code attended} distingue "se dictó" de
 * "el estudiante no llegó". Lo escucha billing para liberar (o no) el dinero retenido.
 */
public record BookingCompletedEvent(UUID bookingId, boolean attended) {
}
