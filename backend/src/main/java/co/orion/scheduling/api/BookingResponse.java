package co.orion.scheduling.api;

import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BusinessZone;

public record BookingResponse(UUID id,
                              UUID professorId,
                              UUID studentId,
                              ZonedDateTime startsAt,
                              ZonedDateTime endsAt,
                              String modality,
                              String status,
                              String locationNote) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getProfessorId(),
                booking.getStudentId(),
                // Se guarda en UTC pero se devuelve en hora de Bogotá, como los cupos.
                booking.getStartsAt().atZone(BusinessZone.BOGOTA),
                booking.getEndsAt().atZone(BusinessZone.BOGOTA),
                booking.getModality().name(),
                booking.getStatus().name(),
                booking.getLocationNote());
    }
}
