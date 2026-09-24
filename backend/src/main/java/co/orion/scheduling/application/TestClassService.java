package co.orion.scheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.time.ClassLength;

/**
 * Una clase de prueba, creada por un administrador, para ensayar el aula sin pasar por la pasarela.
 *
 * <p><strong>Por qué existe.</strong> Probar la videollamada exigía una clase confirmada de verdad:
 * reservar, pagar con Wompi y coordinar a dos personas. Eso convierte cada prueba del aula en una
 * tarde de trabajo, y el resultado era que el aula no se probaba. Esto la deja en un clic.
 *
 * <p><strong>Qué NO hace.</strong> No crea pago, no mueve dinero y no toca las ganancias de nadie.
 * La reserva nace confirmada y marcada como {@code is_trial}, que es la bandera que ya existía para
 * exactamente esto. Sin fila en {@code payments} no hay nada que liquidar ni que aparecer en
 * «Mis ganancias»: una prueba no puede contaminar la contabilidad.
 *
 * <p><strong>Qué sí respeta.</strong> Las mismas invariantes que una clase real. Si el profesor ya
 * tiene algo a esa hora, el índice único lo rechaza igual — una clase de prueba que pisa una clase
 * de verdad sería peor que no poder probar.
 */
@Service
public class TestClassService {

    private static final String IDIOMA_DEL_ENSAYO = "EN";

    private final BookingRepository bookings;
    private final UserRepository users;
    private final MeetingLinkProvider meetingLinks;
    private final Clock clock;

    public TestClassService(BookingRepository bookings,
                            UserRepository users,
                            MeetingLinkProvider meetingLinks,
                            Clock clock) {
        this.bookings = bookings;
        this.users = users;
        this.meetingLinks = meetingLinks;
        this.clock = clock;
    }

    @Transactional
    public Booking create(User admin, String studentEmail, String professorEmail, Instant startsAt) {
        Pareja pareja = pareja(studentEmail, professorEmail);

        // Al minuto en punto: los cupos reales empiezan así, y una prueba que empieza a las 20:03
        // no prueba lo mismo.
        Instant inicio = (startsAt == null ? clock.instant() : startsAt).truncatedTo(ChronoUnit.MINUTES);

        Booking booking = new Booking(pareja.student().getId(), pareja.professor().getId(),
                inicio, inicio.plus(ClassLength.DURATION),
                BookingModality.VIRTUAL, null, null, admin.getId(), inicio);
        booking.markAsTrial();
        booking.confirmPayment();

        Booking saved = guardarOPerderLaCarrera(booking);
        saved.assignMeetingLink(meetingLinks.linkFor(saved.getId()));

        // Deliberadamente NO se publica BookingCreatedEvent: una clase de prueba no manda correos
        // de confirmación ni invitaciones de calendario a nadie.
        return bookings.save(saved);
    }

    /**
     * Una clase de prueba que ya se dictó: empezó hace una hora, termina ahora y queda cerrada. Es el
     * ensayo del acta y de la práctica (Bloque 10), que solo existen después de la clase — sin esto,
     * probarlos exigía dar una clase de una hora y esperar a que se cerrara sola.
     *
     * <p>Nace COMPLETED en un solo INSERT. El índice único del cupo mira solo las clases confirmadas
     * y las que esperan pago, así que el ensayo no choca con la clase real que el profesor tenga a
     * esa hora. El idioma es inglés, el único que Orión enseña (V42): el acta y la práctica lo
     * guardan. Igual que la otra, no publica eventos: nadie recibe correos de una clase que no fue.
     */
    @Transactional
    public Booking createHeld(User admin, String studentEmail, String professorEmail) {
        Pareja pareja = pareja(studentEmail, professorEmail);

        Instant ahora = clock.instant();
        Instant fin = ahora.truncatedTo(ChronoUnit.MINUTES);
        Instant inicio = fin.minus(ClassLength.DURATION);
        Booking booking = new Booking(pareja.student().getId(), pareja.professor().getId(),
                inicio, fin, BookingModality.VIRTUAL, null, IDIOMA_DEL_ENSAYO, admin.getId(), inicio);
        booking.markAsTrial();
        booking.confirmPayment();
        booking.closeWithAttendance(true, ahora);
        return bookings.saveAndFlush(booking);
    }

    private record Pareja(User student, User professor) {
    }

    private Pareja pareja(String studentEmail, String professorEmail) {
        User student = buscar(studentEmail, "estudiante");
        User professor = buscar(professorEmail, "profesor");

        if (student.getId().equals(professor.getId())) {
            throw new UnprocessableException(
                    "El estudiante y el profesor no pueden ser la misma cuenta: la prueba necesita "
                            + "dos sesiones distintas para ver quién modera.");
        }
        if (professor.getRole() != UserRole.PROFESSOR) {
            throw new UnprocessableException(
                    professorEmail + " no tiene rol PROFESSOR. Cámbialo desde Usuarios y repite.");
        }
        return new Pareja(student, professor);
    }

    /**
     * Misma doctrina que {@code BookingService}: el árbitro final de la doble reserva es el índice
     * único, y su violación se traduce a 409. Sin esto, chocar con una clase existente salía como
     * un 500 mudo — que es justo lo que este servicio existe para no hacerle pasar a nadie.
     */
    private Booking guardarOPerderLaCarrera(Booking booking) {
        try {
            return bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    "Ese profesor ya tiene una clase a esa hora. Elige otra hora para la prueba.");
        }
    }

    private User buscar(String correo, String queEs) {
        if (correo == null || correo.isBlank()) {
            throw new UnprocessableException("Falta el correo del " + queEs + ".");
        }
        return users.findByEmailIgnoreCase(correo.trim())
                .orElseThrow(() -> new UnprocessableException(
                        "No hay ninguna cuenta con el correo " + correo.trim()
                                + ". Créala primero desde Usuarios."));
    }
}
