package co.orion.notifications.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCancelledEvent;
import co.orion.scheduling.domain.BookingCreatedEvent;
import co.orion.scheduling.persistence.BookingRepository;

/**
 * Convierte eventos de reserva en correos.
 *
 * AFTER_COMMIT, y no dentro de la transacción, por dos razones simétricas:
 *  - un fallo del servidor de correo jamás debe tumbar ni revertir una reserva ya hecha
 *    (de ahí el try/catch: se registra el error y la reserva sigue en pie);
 *  - jamás se debe notificar una reserva que acabó haciendo rollback — si la transacción no
 *    confirma, este listener ni siquiera se ejecuta.
 *
 * @Async además de AFTER_COMMIT: el commit responde de inmediato y los correos salen en un hilo
 * aparte. Así la latencia del SMTP (o un servidor lento) nunca hace esperar la reserva. Los tests
 * usan verify(..., timeout(...)) para no depender del momento exacto del envío.
 */
@Component
public class BookingNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationListener.class);

    private final BookingRepository bookings;
    private final UserRepository users;
    private final BookingEmailComposer composer;
    private final BookingMailSender mailSender;

    public BookingNotificationListener(BookingRepository bookings,
                                       UserRepository users,
                                       BookingEmailComposer composer,
                                       BookingMailSender mailSender) {
        this.bookings = bookings;
        this.users = users;
        this.composer = composer;
        this.mailSender = mailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCreated(BookingCreatedEvent event) {
        participants(event.bookingId()).ifPresent(trio -> {
            send(composer.confirmation(trio.booking(), trio.student(), trio.professor(), true));
            send(composer.confirmation(trio.booking(), trio.professor(), trio.student(), false));
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        participants(event.bookingId()).ifPresent(trio -> {
            User cancelledBy = users.findById(trio.booking().getCancelledBy()).orElse(trio.student());
            send(composer.cancellation(trio.booking(), trio.student(), trio.professor(), cancelledBy));
            send(composer.cancellation(trio.booking(), trio.professor(), trio.student(), cancelledBy));
        });
    }

    /** El correo es un efecto secundario deseable, no una condición para que la reserva exista. */
    private void send(BookingEmail email) {
        try {
            mailSender.send(email);
            log.info("Correo enviado a {}: {}", email.to(), email.subject());
        } catch (Exception ex) {
            log.error("No se pudo enviar el correo a {} ({}). La reserva no se ve afectada.",
                    email.to(), email.subject(), ex);
        }
    }

    private record Participants(Booking booking, User student, User professor) {
    }

    private Optional<Participants> participants(java.util.UUID bookingId) {
        Optional<Booking> booking = bookings.findById(bookingId);
        if (booking.isEmpty()) {
            log.error("Evento de una reserva inexistente: {}", bookingId);
            return Optional.empty();
        }
        Optional<User> student = users.findById(booking.get().getStudentId());
        Optional<User> professor = users.findById(booking.get().getProfessorId());
        if (student.isEmpty() || professor.isEmpty()) {
            log.error("Reserva {} sin participantes válidos", bookingId);
            return Optional.empty();
        }
        return Optional.of(new Participants(booking.get(), student.get(), professor.get()));
    }
}
