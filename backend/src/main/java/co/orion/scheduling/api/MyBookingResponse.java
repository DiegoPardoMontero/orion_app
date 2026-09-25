package co.orion.scheduling.api;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import co.orion.identity.domain.User;
import co.orion.scheduling.domain.Booking;
import co.orion.shared.time.BusinessZone;

/**
 * Lo que ve cada parte de su propia clase.
 *
 * <p>{@code canCancel} y {@code lateCancel} son dos cosas distintas y hay que leerlas juntas.
 * <strong>Cancelar siempre se puede</strong> mientras la clase siga activa; lo que decide la
 * ventana de anticipación —la que corresponde a QUIEN mira, configurable en platform_settings— es
 * si la cancelación es «tardía», y con eso, qué pasa con el dinero.
 *
 * <p>Antes {@code canCancel} se ponía en falso dentro de la ventana y el botón se deshabilitaba.
 * Era peor para todos: al estudiante que ya sabía que no iba a ir se le obligaba a dejar la clase
 * en pie, y el profesor se enteraba esperando delante de una sala vacía.
 *
 * <p>El frontend solo pinta y avisa — jamás reimplementa la política.
 */
public record MyBookingResponse(UUID id,
                                ZonedDateTime startsAt,
                                ZonedDateTime endsAt,
                                String modality,
                                String status,
                                String locationNote,
                                String meetingLink,
                                boolean canCancel,
                                /** Dentro de la ventana: se puede cancelar, pero con consecuencia. */
                                boolean lateCancel,
                                Counterpart counterpart,
                                /** Ensayo del admin (aula o acta): no se califica ni cuenta. */
                                boolean rehearsal,
                                /** La clase de prueba del estudiante (Q7): una clase de verdad, gratis desde la V65. */
                                boolean trial) {

    /**
     * La otra parte: el profesor si mira un estudiante, el estudiante si mira un profesor. La foto
     * y el titular solo llegan cuando la contraparte es profesor (los estudiantes aún no tienen
     * perfil público); si no, van en null y la UI cae al avatar de iniciales.
     *
     * NO lleva el teléfono, y es el punto: el Bloque 3 llevó el contacto dentro de Orión y
     * enmascara los números en los mensajes, pero este DTO se lo entregaba igual a quien reservara
     * una clase. Con los pagos encendidos eso ya no era solo una fuga de contacto — era la comisión
     * de todas las clases siguientes, que se acordaban por fuera.
     */
    public record Counterpart(UUID id, String fullName, String photoUrl, String headline) {

        static Counterpart of(User user, String photoUrl, String headline) {
            return new Counterpart(user.getId(), user.getFullName(), photoUrl, headline);
        }
    }

    public static MyBookingResponse of(Booking booking, User counterpart,
                                       String counterpartPhotoUrl, String counterpartHeadline,
                                       Instant now, Duration cancellationWindow) {
        return new MyBookingResponse(
                booking.getId(),
                booking.getStartsAt().atZone(BusinessZone.BOGOTA),
                booking.getEndsAt().atZone(BusinessZone.BOGOTA),
                booking.getModality().name(),
                booking.getStatus().name(),
                booking.getLocationNote(),
                booking.getMeetingLink(),
                !booking.getStatus().isTerminal(),
                booking.isConfirmed() && !booking.isCancellableAt(now, cancellationWindow),
                Counterpart.of(counterpart, counterpartPhotoUrl, counterpartHeadline),
                booking.isRehearsal(),
                booking.isTrial());
    }
}
