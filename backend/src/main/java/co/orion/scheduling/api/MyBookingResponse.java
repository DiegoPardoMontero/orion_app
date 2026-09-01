package co.orion.scheduling.api;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.identity.domain.User;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BusinessZone;

/**
 * canCancel lo decide el servidor: la regla de 24 horas vive una sola vez, en el dominio.
 * El frontend solo pinta el botón — jamás reimplementa la política.
 */
public record MyBookingResponse(UUID id,
                                ZonedDateTime startsAt,
                                ZonedDateTime endsAt,
                                String modality,
                                String status,
                                String locationNote,
                                String meetingLink,
                                boolean canCancel,
                                Counterpart counterpart) {

    /**
     * La otra parte: el profesor si mira un estudiante, el estudiante si mira un profesor. La foto
     * y el titular solo llegan cuando la contraparte es profesor (los estudiantes aún no tienen
     * perfil público); si no, van en null y la UI cae al avatar de iniciales.
     */
    public record Counterpart(UUID id, String fullName, String whatsappPhone,
                              String photoUrl, String headline) {

        static Counterpart of(User user, String photoUrl, String headline) {
            return new Counterpart(user.getId(), user.getFullName(), user.getWhatsappPhone(),
                    photoUrl, headline);
        }
    }

    public static MyBookingResponse of(Booking booking, User counterpart,
                                       String counterpartPhotoUrl, String counterpartHeadline,
                                       Instant now) {
        return new MyBookingResponse(
                booking.getId(),
                booking.getStartsAt().atZone(BusinessZone.BOGOTA),
                booking.getEndsAt().atZone(BusinessZone.BOGOTA),
                booking.getModality().name(),
                booking.getStatus().name(),
                booking.getLocationNote(),
                booking.getMeetingLink(),
                booking.isCancellableAt(now),
                Counterpart.of(counterpart, counterpartPhotoUrl, counterpartHeadline));
    }
}
