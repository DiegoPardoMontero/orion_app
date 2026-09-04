package co.orion.scheduling.api;

import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.scheduling.application.AdminBookingService.AdminBookingView;
import co.orion.shared.time.BusinessZone;

public record AdminBookingResponse(UUID id,
                                   ZonedDateTime startsAt,
                                   ZonedDateTime endsAt,
                                   String studentName,
                                   String professorName,
                                   String modality,
                                   String status,
                                   boolean selfService) {

    public static AdminBookingResponse from(AdminBookingView view) {
        var booking = view.booking();
        return new AdminBookingResponse(
                booking.getId(),
                booking.getStartsAt().atZone(BusinessZone.BOGOTA),
                booking.getEndsAt().atZone(BusinessZone.BOGOTA),
                view.student() != null ? view.student().getFullName() : "—",
                view.professor() != null ? view.professor().getFullName() : "—",
                booking.getModality().name(),
                booking.getStatus().name(),
                // La reserva la hizo el propio estudiante, no un admin en su nombre.
                booking.isSelfService());
    }
}
