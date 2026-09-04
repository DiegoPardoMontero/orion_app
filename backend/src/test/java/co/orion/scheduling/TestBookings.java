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

    /**
     * El idioma por defecto de una reserva de prueba. Casi ningún test habla de idiomas, y
     * obligarles a pasarlo llenaría doce ficheros de un "EN" que no dice nada; los que sí lo
     * necesitan usan las sobrecargas que lo reciben.
     */
    public static final String DEFAULT_LANGUAGE = "EN";

    private TestBookings() {
    }

    public static Booking confirmed(UUID studentId,
                                    UUID professorId,
                                    Instant startsAt,
                                    Instant endsAt,
                                    BookingModality modality,
                                    String locationNote,
                                    UUID createdBy) {
        return confirmed(studentId, professorId, startsAt, endsAt, modality, locationNote,
                DEFAULT_LANGUAGE, createdBy);
    }

    public static Booking confirmed(UUID studentId,
                                    UUID professorId,
                                    Instant startsAt,
                                    Instant endsAt,
                                    BookingModality modality,
                                    String locationNote,
                                    String languageCode,
                                    UUID createdBy) {
        Booking booking = awaitingPayment(studentId, professorId, startsAt, endsAt, modality,
                locationNote, languageCode, createdBy);
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
        return awaitingPayment(studentId, professorId, startsAt, endsAt, modality, locationNote,
                DEFAULT_LANGUAGE, createdBy);
    }

    public static Booking awaitingPayment(UUID studentId,
                                          UUID professorId,
                                          Instant startsAt,
                                          Instant endsAt,
                                          BookingModality modality,
                                          String locationNote,
                                          String languageCode,
                                          UUID createdBy) {
        return new Booking(studentId, professorId, startsAt, endsAt, modality, locationNote,
                languageCode, createdBy, startsAt.minus(ANY_HOLD));
    }
}
