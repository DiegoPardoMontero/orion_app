package co.orion.scheduling.api;

import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.scheduling.application.BookingService;
import co.orion.scheduling.domain.Booking;
import co.orion.shared.time.BusinessZone;

public record BookingResponse(UUID id,
                              UUID professorId,
                              UUID studentId,
                              ZonedDateTime startsAt,
                              ZonedDateTime endsAt,
                              String modality,
                              String status,
                              String locationNote,
                              ZonedDateTime expiresAt,
                              PaymentTicketResponse payment) {

    /** Para las respuestas que no crean cobro (cancelar, reprogramar): sin datos de pago. */
    public static BookingResponse from(Booking booking) {
        return build(booking, null);
    }

    /** Para POST /bookings: la reserva nace PENDING_PAYMENT y viene con a dónde ir a pagarla. */
    public static BookingResponse created(BookingService.NewBooking created) {
        return build(created.booking(), PaymentTicketResponse.from(created.ticket()));
    }

    private static BookingResponse build(Booking booking, PaymentTicketResponse payment) {
        return new BookingResponse(
                booking.getId(),
                booking.getProfessorId(),
                booking.getStudentId(),
                // Se guarda en UTC pero se devuelve en hora de Bogotá, como los cupos.
                booking.getStartsAt().atZone(BusinessZone.BOGOTA),
                booking.getEndsAt().atZone(BusinessZone.BOGOTA),
                booking.getModality().name(),
                booking.getStatus().name(),
                booking.getLocationNote(),
                booking.getExpiresAt() != null
                        ? booking.getExpiresAt().atZone(BusinessZone.BOGOTA) : null,
                payment);
    }
}
