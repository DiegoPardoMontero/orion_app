package co.orion.messaging.application;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCancelledEvent;
import co.orion.scheduling.domain.BookingCreatedEvent;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.FechasEnPalabras;

/**
 * La reserva y la cancelación, también en la campana (24/09/2026). Antes solo salía el correo: quien
 * tenía la app abierta no se enteraba ahí de una clase nueva ni de una que se cayó.
 *
 * <p>{@code AFTER_COMMIT} + {@code REQUIRES_NEW}, como el resto de los avisos: nunca se avisa de algo
 * que hizo rollback, y un fallo aquí no tumba la reserva.
 */
@Component
public class AvisosDeReserva {

    private final NotificationService notifications;
    private final BookingRepository bookings;
    private final UserRepository users;

    public AvisosDeReserva(NotificationService notifications, BookingRepository bookings, UserRepository users) {
        this.notifications = notifications;
        this.bookings = bookings;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCreated(BookingCreatedEvent event) {
        Booking b = bookings.findById(event.bookingId()).orElse(null);
        if (b == null || b.isTrial()) {
            return;
        }
        String cuando = cuando(b);
        String enlace = "/mis-clases?clase=" + b.getId();
        notifications.create(b.getStudentId(), "BOOKING_CREATED",
                "Tu clase con " + nombre(b.getProfessorId()) + " quedó agendada",
                capital(cuando) + ". Entras desde «Mis clases» a la hora de la clase.", enlace);
        notifications.create(b.getProfessorId(), "BOOKING_RECEIVED",
                "Nueva clase con " + nombre(b.getStudentId()),
                capital(cuando) + ". Ya está en tu agenda.", enlace);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCancelled(BookingCancelledEvent event) {
        Booking b = bookings.findById(event.bookingId()).orElse(null);
        if (b == null || b.isTrial()) {
            return;
        }
        UUID quien = b.getCancelledBy();
        String cuando = cuando(b);
        String enlace = "/mis-clases?clase=" + b.getId() + "&scope=past";
        // A quien canceló, la confirmación; al otro, el aviso con quién fue.
        for (UUID destino : new UUID[] {b.getStudentId(), b.getProfessorId()}) {
            UUID otro = destino.equals(b.getStudentId()) ? b.getProfessorId() : b.getStudentId();
            String titulo = destino.equals(quien)
                    ? "Cancelaste tu clase con " + nombre(otro)
                    : nombre(otro) + " canceló la clase";
            // Tipos distintos porque solo uno de los dos avisos suena en el dispositivo: el de quien se
            // enteró, no la confirmación de lo que uno mismo acaba de hacer.
            notifications.create(destino, destino.equals(quien) ? "BOOKING_CANCELLED_SELF" : "BOOKING_CANCELLED",
                    titulo, "Era el " + cuando + ".", enlace);
        }
    }

    private static String cuando(Booking b) {
        return FechasEnPalabras.dia(b.getStartsAt()) + " a las " + FechasEnPalabras.hora(b.getStartsAt());
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String nombre(UUID userId) {
        return users.findById(userId).map(User::getFullName).orElse("Tu contraparte");
    }
}
