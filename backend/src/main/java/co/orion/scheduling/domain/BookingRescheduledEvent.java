package co.orion.scheduling.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Se publica cuando la contraparte acepta mover una clase. Es un evento propio y no un
 * {@link BookingCreatedEvent} reutilizado: al crear, cinco listeners reaccionan (confirmación con
 * .ics, puntos, el saludo de Rigel, el saludo al reservar, los avisos de reserva) y ninguno de esos
 * tiene sentido para una clase que ya existía y solo cambió de hora. Lo único que corresponde es
 * avisarles a los dos la hora nueva.
 *
 * @param previousStartsAt la hora que tenía, para decirla en el correo
 */
public record BookingRescheduledEvent(UUID bookingId, Instant previousStartsAt) {
}
