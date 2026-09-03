package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.scheduling.domain.AttendanceRecord;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCompletedEvent;
import co.orion.scheduling.persistence.AttendanceRecordRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

@Service
public class AttendanceService {

    private final BookingRepository bookings;
    private final AttendanceRecordRepository attendance;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AttendanceService(BookingRepository bookings,
                             AttendanceRecordRepository attendance,
                             ApplicationEventPublisher events,
                             Clock clock) {
        this.bookings = bookings;
        this.attendance = attendance;
        this.events = events;
        this.clock = clock;
    }

    /** La reserva va junto al registro: el llamador necesita el estado en el que quedó. */
    public record AttendanceResult(AttendanceRecord record, Booking booking) {
    }

    /** Registra asistencia y cierra la reserva: COMPLETED si asistió, NO_SHOW_STUDENT si no. */
    @Transactional
    public AttendanceResult record(User professor, UUID bookingId, boolean present, String notes) {
        Booking booking = bookings.findById(bookingId)
                .filter(candidate -> candidate.getProfessorId().equals(professor.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        Instant now = clock.instant();
        if (!booking.hasEndedAt(now)) {
            throw new UnprocessableException("La clase aún no termina");
        }
        // Una clase cancelada, ya registrada o en revisión no admite registro de asistencia.
        if (!booking.isConfirmed()) {
            throw new ConflictException("La reserva no está confirmada: no admite registro de asistencia");
        }

        booking.closeWithAttendance(present, now);
        Booking closed = bookings.save(booking);

        AttendanceRecord record = attendance.save(
                new AttendanceRecord(booking.getId(), present, notes, now));
        // Registrar la asistencia es lo que convierte el dinero retenido en dinero ganado. Va por
        // evento, como los correos: este servicio no sabe que existe una comisión.
        events.publishEvent(new BookingCompletedEvent(closed.getId(), present));
        return new AttendanceResult(record, closed);
    }
}
