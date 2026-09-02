package co.orion.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;

/**
 * Reservas de prueba. Desde el Bloque 4 una reserva nace PENDING_PAYMENT y solo llega a CONFIRMED
 * pasando por {@code confirmPayment()}: no hay atajo, ni siquiera en los tests. Este helper hace
 * ese recorrido en una línea para los tests que necesitan una clase ya confirmada como punto de
 * partida (asistencia, reseñas, cancelación).
 */
public final class TestBookings {

    /** Un plazo de pago cualquiera: al confirmar se borra, así que su valor no influye en nada. */
    private static final Duration ANY_HOLD = Duration.ofMinutes(20);

    private TestBookings() {
    }

    public static Booking confirmed(UUID studentId,
                                    UUID professorId,
                                    Instant startsAt,
                                    Instant endsAt,
                                    BookingModality modality,
                                    String locationNote,
                                    UUID createdBy) {
        Booking booking = awaitingPayment(
                studentId, professorId, startsAt, endsAt, modality, locationNote, createdBy);
        booking.confirmPayment();
        return booking;
    }

    public static Booking confirmed(UUID studentId,
                                    UUID professorId,
                                    Instant startsAt,
                                    BookingModality modality,
                                    String locationNote,
                                    UUID createdBy) {
        return confirmed(studentId, professorId, startsAt, startsAt.plus(Duration.ofHours(1)),
                modality, locationNote, createdBy);
    }

    public static Booking awaitingPayment(UUID studentId,
                                          UUID professorId,
                                          Instant startsAt,
                                          Instant endsAt,
                                          BookingModality modality,
                                          String locationNote,
                                          UUID createdBy) {
        return new Booking(studentId, professorId, startsAt, endsAt, modality, locationNote,
                createdBy, startsAt.minus(ANY_HOLD));
    }
}
