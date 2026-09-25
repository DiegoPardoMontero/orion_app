package co.orion.messaging.application;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.lifecycle.domain.LessonCompletedEvent;
import co.orion.messaging.domain.RigelKind;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCompletedEvent;
import co.orion.scheduling.domain.BookingCreatedEvent;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.FechasEnPalabras;

/**
 * Rigel acompaña los primeros pasos: la primera reserva y la primera clase, de cada lado. Cada uno
 * sale una sola vez por persona —lo decide el índice de la V66—, así que escuchar todas las
 * reservas y todas las clases no repite nada. Un fallo se registra y se traga: un mensaje de Rigel
 * nunca tumba una reserva.
 */
@Component
public class RigelEnLosPrimerosPasos {

    private static final Logger log = LoggerFactory.getLogger(RigelEnLosPrimerosPasos.class);

    private final RigelService rigel;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final Clock clock;

    public RigelEnLosPrimerosPasos(RigelService rigel, BookingRepository bookings, UserRepository users,
                                   Clock clock) {
        this.rigel = rigel;
        this.bookings = bookings;
        this.users = users;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingCreatedEvent event) {
        seguro("primera reserva", () -> bookings.findById(event.bookingId())
                .filter(b -> !b.isRehearsal())
                .ifPresent(b -> {
                    rigel.enviar(b.getStudentId(), RigelKind.FIRST_BOOKING_STUDENT,
                            nombre("profesor", b.getProfessorId()));
                    rigel.enviar(b.getProfessorId(), RigelKind.FIRST_BOOKING_PROFESSOR,
                            nombre("estudiante", b.getStudentId()));
                }));
    }

    /** La clase se cerró sola o sin novedad: ya se dictó. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LessonCompletedEvent event) {
        primeraClase(event.bookingId());
    }

    /** El profesor marcó la asistencia: cuenta si la clase ya terminó y el estudiante vino. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingCompletedEvent event) {
        if (event.attended()) {
            primeraClase(event.bookingId());
        }
    }

    private void primeraClase(UUID bookingId) {
        seguro("primera clase", () -> bookings.findById(bookingId)
                .filter(b -> !b.isRehearsal() && !b.getEndsAt().isAfter(clock.instant()))
                .ifPresent((Booking b) -> {
                    rigel.enviar(b.getStudentId(), RigelKind.FIRST_CLASS_STUDENT,
                            nombre("profesor", b.getProfessorId()));
                    rigel.enviar(b.getProfessorId(), RigelKind.FIRST_CLASS_PROFESSOR,
                            nombre("estudiante", b.getStudentId()));
                }));
    }

    /** El primer nombre de la otra persona; sin nombre, el texto dice «tu profe» o «tu estudiante». */
    private Map<String, String> nombre(String clave, UUID userId) {
        return users.findById(userId).map(User::getFullName).map(FechasEnPalabras::primerNombre)
                .filter(n -> !n.isBlank())
                .map(n -> Map.of(clave, n))
                .orElse(Map.of());
    }

    private void seguro(String que, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException ex) {
            log.warn("Rigel no pudo escribir ({}): {}", que, ex.getMessage());
        }
    }
}
