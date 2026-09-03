package co.orion.scheduling.application;

import java.time.Duration;
import java.time.Instant;

import co.orion.identity.domain.User;
import co.orion.scheduling.domain.Booking;

/**
 * Una reserva junto a la contraparte que le corresponde ver a quien consulta. La foto y el titular
 * solo viajan cuando la contraparte es un profesor (los estudiantes aún no tienen perfil público);
 * en otro caso van en null y la UI cae al avatar de iniciales.
 */
public record MyBookingsView(
        Booking booking,
        User counterpart,
        String counterpartPhotoUrl,
        String counterpartHeadline,
        Instant now,
        /** La ventana de cancelación de QUIEN mira: el estudiante y el profesor tienen la suya. */
        Duration cancellationWindow) {
}
