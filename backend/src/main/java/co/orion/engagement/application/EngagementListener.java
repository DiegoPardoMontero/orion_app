package co.orion.engagement.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.domain.StudentProfileUpdatedEvent;
import co.orion.lifecycle.domain.LessonCompletedEvent;
import co.orion.scheduling.domain.BookingCreatedEvent;

/**
 * La única puerta por la que entra algo a {@code engagement}: escucha lo que ya publican los demás
 * módulos y no llama a ninguno. Esa dirección es deliberada — la gamificación puede desaparecer
 * entera sin romper el marketplace, y ningún módulo tiene que saber qué es un punto.
 *
 * <p>{@code AFTER_COMMIT} con {@code REQUIRES_NEW}, como el listener de correos: sin la transacción
 * nueva, todo lo que se escriba aquí se descarta en silencio, porque la transacción original ya
 * está cerrada cuando llega el evento.
 *
 * <p>Un fallo aquí se registra y se traga. Que la gamificación falle nunca puede tumbar una clase
 * que ya se dio ni una reserva que ya se pagó.
 */
@Component
public class EngagementListener {

    private static final Logger log = LoggerFactory.getLogger(EngagementListener.class);

    private final AchievementService achievements;

    public EngagementListener(AchievementService achievements) {
        this.achievements = achievements;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LessonCompletedEvent event) {
        seguro(() -> achievements.onLessonCompleted(
                event.studentId(), event.bookingId(), event.completedAt()),
                "clase completada " + event.bookingId());
    }

    /** Reservar no da puntos: solo enciende «Primera reserva». */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingCreatedEvent event) {
        seguro(() -> achievements.onBookingCreated(event.bookingId()),
                "reserva creada " + event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(StudentProfileUpdatedEvent event) {
        seguro(() -> achievements.onSomethingHappened(event.studentId()),
                "ficha actualizada de " + event.studentId());
    }

    private void seguro(Runnable accion, String que) {
        try {
            accion.run();
        } catch (RuntimeException ex) {
            log.error("No se pudo actualizar la gamificación tras {}. "
                    + "La operación de negocio no se ve afectada.", que, ex);
        }
    }
}
