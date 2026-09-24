package co.orion.messaging.application;

import java.time.Clock;
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
import co.orion.lifecycle.domain.ClassReminderDueEvent;
import co.orion.lifecycle.domain.LessonCompletedEvent;
import co.orion.practice.domain.PracticeReadyEvent;
import co.orion.practice.domain.PracticeReminderDueEvent;
import co.orion.reputation.domain.ReviewCreatedEvent;
import co.orion.reputation.persistence.ReviewRepository;
import co.orion.scheduling.domain.BookingCompletedEvent;
import co.orion.scheduling.domain.BookingExpiredEvent;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.FechasEnPalabras;

/**
 * Lo que pasa alrededor de una clase, en la campana (24/09/2026): el recordatorio del día antes y
 * el de una hora antes, a los dos; «¿cómo te fue?» al terminar y otra vez al día siguiente si no la
 * calificó; la práctica lista; la reseña que recibe el profesor; y la reserva que venció sin pago.
 *
 * <p>Los correos de lo mismo van aparte, en {@code notifications}; el dispositivo, en {@code push}.
 * Un fallo aquí se registra y se traga: un aviso nunca tumba lo que avisa.
 */
@Component
public class AvisosDeClase {

    private static final Logger log = LoggerFactory.getLogger(AvisosDeClase.class);

    private final NotificationService notifications;
    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final Clock clock;

    public AvisosDeClase(NotificationService notifications, BookingRepository bookings, ReviewRepository reviews,
                         UserRepository users, Clock clock) {
        this.notifications = notifications;
        this.bookings = bookings;
        this.reviews = reviews;
        this.users = users;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ClassReminderDueEvent event) {
        seguro("recordatorio " + event.kind() + " de " + event.bookingId(), () -> bookings.findById(event.bookingId())
                .ifPresent(b -> {
                    String cuando = FechasEnPalabras.cuando(b.getStartsAt(), clock.instant());
                    String clase = "/mis-clases?clase=" + b.getId();
                    switch (event.kind()) {
                        case DAY_BEFORE -> {
                            notifications.create(b.getStudentId(), "CLASS_REMINDER",
                                    "Tu clase con " + nombre(b.getProfessorId()) + " es " + cuando,
                                    "Entras desde «Mis clases» cuando falten unos minutos. Si no puedes ir, cancélala"
                                            + " con tiempo: así el cupo le sirve a otra persona.", clase);
                            notifications.create(b.getProfessorId(), "CLASS_REMINDER",
                                    "Tu clase con " + nombre(b.getStudentId()) + " es " + cuando,
                                    "Échale un vistazo a su ficha antes de empezar.", clase);
                        }
                        case HOUR_BEFORE -> {
                            String aula = "/mis-clases/" + b.getId() + "/aula";
                            notifications.create(b.getStudentId(), "CLASS_SOON",
                                    "En una hora: tu clase con " + nombre(b.getProfessorId()),
                                    "Empieza a las " + FechasEnPalabras.hora(b.getStartsAt())
                                            + ". Busca un lugar tranquilo y con buena señal.", aula);
                            notifications.create(b.getProfessorId(), "CLASS_SOON",
                                    "En una hora: tu clase con " + nombre(b.getStudentId()),
                                    "Empieza a las " + FechasEnPalabras.hora(b.getStartsAt()) + ".", aula);
                        }
                        case RATE -> {
                            if (!reviews.existsByBookingId(b.getId())) {
                                notifications.create(b.getStudentId(), "RATE_REMINDER",
                                        "¿Le das tu calificación a " + nombre(b.getProfessorId()) + "?",
                                        "Un toque y listo: +20 puntos para ti, y a los demás les ayuda a elegir.",
                                        "/mis-clases?clase=" + b.getId() + "&scope=past");
                            }
                        }
                    }
                }));
    }

    /**
     * La clase terminó: la primera invitación a calificarla, mientras está fresca. Llega por una de dos
     * puertas —el cierre automático o la asistencia que registra el profesor—, nunca por las dos: el
     * cierre automático solo toma las que siguen abiertas.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LessonCompletedEvent event) {
        pedirCalificacion(event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingCompletedEvent event) {
        // Si el profe marcó la asistencia a mitad de clase, preguntar ahora sería interrumpirla: la
        // invitación queda para el recordatorio del día siguiente.
        boolean yaTermino = bookings.findById(event.bookingId())
                .map(b -> !b.getEndsAt().isAfter(clock.instant())).orElse(false);
        if (event.attended() && yaTermino) {
            pedirCalificacion(event.bookingId());
        }
    }

    private void pedirCalificacion(UUID bookingId) {
        seguro("clase terminada " + bookingId, () -> bookings.findById(bookingId)
                .filter(b -> !b.isRehearsal() && !reviews.existsByBookingId(b.getId()))
                .ifPresent(b -> notifications.create(b.getStudentId(), "RATE_REQUEST",
                        "¿Cómo te fue con " + nombre(b.getProfessorId()) + "?",
                        "Califica tu clase: +20 puntos, y le ayudas a quien busca profe.",
                        "/mis-clases?clase=" + b.getId() + "&scope=past")));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ReviewCreatedEvent event) {
        seguro("reseña " + event.reviewId(), () -> notifications.create(event.professorId(), "REVIEW_RECEIVED",
                nombre(event.studentId()) + " calificó su clase contigo: " + "★".repeat(event.rating()),
                "Tu calificación y lo que dicen de ti están en «Mi desempeño».", "/desempeno"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PracticeReadyEvent event) {
        seguro("práctica " + event.setId(), () -> notifications.create(event.studentId(), "PRACTICE_READY",
                "Tu práctica está lista ✦",
                event.itemCount() + " ejercicios de tu clase con " + nombre(event.professorId())
                        + ", unos " + Math.max(2, event.itemCount()) + " minutos. Suma a tu racha.",
                "/practica/" + event.setId()));
    }

    /** A los dos días sin empezarla, una vez y solo en la campana (el de «lista» ya sonó). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PracticeReminderDueEvent event) {
        seguro("recordar la práctica " + event.setId(), () -> notifications.create(event.studentId(),
                "PRACTICE_REMINDER", "Tu práctica con " + nombre(event.professorId()) + " sigue esperándote",
                event.itemCount() + " ejercicios, unos " + Math.max(2, event.itemCount())
                        + " minutos. Cuando la termines, suma a tu racha y a tus puntos.",
                "/practica/" + event.setId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingExpiredEvent event) {
        seguro("reserva vencida " + event.bookingId(), () -> bookings.findById(event.bookingId())
                .ifPresent(b -> notifications.create(b.getStudentId(), "BOOKING_EXPIRED",
                        "Tu reserva con " + nombre(b.getProfessorId()) + " venció sin pago",
                        "El cupo del " + FechasEnPalabras.dia(b.getStartsAt()) + " quedó libre. Si todavía lo"
                                + " quieres, vuelve a reservarlo.",
                        "/profesores/" + b.getProfessorId())));
    }

    private String nombre(UUID userId) {
        return users.findById(userId).map(User::getFullName).map(FechasEnPalabras::primerNombre).orElse("tu contraparte");
    }

    private static void seguro(String que, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar en la campana ({}): {}", que, ex.getMessage());
        }
    }
}
