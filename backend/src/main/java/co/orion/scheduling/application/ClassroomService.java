package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.api.ClassroomResponse;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.time.BusinessZone;
import co.orion.shared.time.ClassLength;

/**
 * Quién puede entrar al aula, cuándo, y con qué poderes.
 *
 * <p><strong>La ventana.</strong> La sala abre diez minutos antes de la hora y el acceso muere
 * quince después del final. Los diez de antes son para que nadie llegue a una puerta cerrada por
 * tener el reloj adelantado, y son también donde se prueba el micrófono. Los quince de después
 * existen porque una clase que se alarga un poco es normal, y porque son los mismos quince tras los
 * cuales se puede reportar una ausencia: fuera de ellos ya no hay nada que hacer en la sala.
 *
 * <p><strong>El moderador es el profesor.</strong> Es la razón entera de haber dejado
 * {@code meet.jit.si}: allí mandaba quien entrara primero, así que un estudiante podía silenciar o
 * expulsar a su propio profesor. Aquí lo decide un token que firma Orión.
 *
 * <p><strong>Siempre responde.</strong> Estar fuera de la ventana no es un error: es un estado que
 * la antesala tiene que poder dibujar, con su cuenta atrás y con la cara de la otra persona. Lo que
 * cambia es que no se emite token. Un 422 aquí dejaría en blanco justo la pantalla de quien llegó
 * temprano porque está nervioso.
 */
@Service
public class ClassroomService {

    /** Cuánto antes de la hora abre la sala. */
    public static final Duration ANTESALA = Duration.ofMinutes(10);

    /** Cuánto después del final deja de servir el acceso. */
    public static final Duration COLA = Duration.ofMinutes(15);

    private final BookingRepository bookings;
    private final UserRepository users;
    private final ProfessorProfileRepository profiles;
    private final JaasTokenMinter minter;
    private final JaasProperties props;
    private final RoomPresence presence;
    private final Clock clock;

    public ClassroomService(BookingRepository bookings,
                            UserRepository users,
                            ProfessorProfileRepository profiles,
                            JaasTokenMinter minter,
                            JaasProperties props,
                            RoomPresence presence,
                            Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.profiles = profiles;
        this.minter = minter;
        this.props = props;
        this.presence = presence;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClassroomResponse enter(UUID bookingId, User quien) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        boolean esProfesor = booking.getProfessorId().equals(quien.getId());
        boolean esEstudiante = booking.getStudentId().equals(quien.getId());
        if (!esProfesor && !esEstudiante) {
            // 404 y no 403: quien no es de esta clase no tiene por qué saber que existe.
            throw new ResourceNotFoundException("Reserva no encontrada");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException(
                    "Esta clase no está confirmada, así que no tiene sala.");
        }

        Instant ahora = clock.instant();
        Instant abre = booking.getStartsAt().minus(ANTESALA);
        Instant caduca = booking.getEndsAt().plus(COLA);

        String estado;
        if (ahora.isBefore(abre)) {
            estado = "CLOSED";
        } else if (!ahora.isBefore(caduca)) {
            estado = "ENDED";
        } else if (ahora.isBefore(booking.getStartsAt())) {
            estado = "OPEN";
        } else {
            estado = "STARTED";
        }

        boolean puedeEntrar = ("OPEN".equals(estado) || "STARTED".equals(estado))
                && minter.disponible();

        UUID otroId = esProfesor ? booking.getStudentId() : booking.getProfessorId();
        User otro = users.findById(otroId).orElse(null);

        return new ClassroomResponse(
                estado,
                enBogota(booking.getStartsAt()),
                enBogota(booking.getEndsAt()),
                enBogota(abre),
                enBogota(caduca),
                ClassLength.MINUTES,
                esProfesor,
                contraparte(otro, esProfesor),
                presence.estaDentro(bookingId, otroId),
                quien.getFullName(),
                puedeEntrar ? props.domain() : null,
                puedeEntrar ? props.appId() + "/" + bookingId : null,
                puedeEntrar
                        ? minter.mint(bookingId.toString(), quien.getId().toString(),
                                quien.getFullName(), quien.getEmail(), quien.getPhotoUrl(),
                                esProfesor, ahora, caduca)
                        : null);
    }

    /** El titular solo lo tiene el profesor; al estudiante no se le inventa uno. */
    private ClassroomResponse.Counterpart contraparte(User otro, boolean elOtroEsEstudiante) {
        if (otro == null) {
            return null;
        }
        String headline = elOtroEsEstudiante ? null : profiles.findById(otro.getId())
                .map(ProfessorProfile::getHeadline).orElse(null);
        return new ClassroomResponse.Counterpart(
                otro.getFullName(), primerNombre(otro.getFullName()), otro.getPhotoUrl(), headline);
    }

    /** «María te espera», no «María Fernanda Gómez te espera». */
    private static String primerNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "";
        }
        int espacio = nombre.trim().indexOf(' ');
        return espacio < 0 ? nombre.trim() : nombre.trim().substring(0, espacio);
    }

    private static ZonedDateTime enBogota(Instant instante) {
        return ZonedDateTime.ofInstant(instante, BusinessZone.BOGOTA);
    }
}
