package co.orion.engagement.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.assessment.domain.AssessmentCompletedEvent;
import co.orion.identity.domain.StudentProfileUpdatedEvent;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;
import co.orion.lifecycle.domain.LessonCompletedEvent;
import co.orion.messaging.application.MessagePostedEvent;
import co.orion.onboarding.domain.OnboardingStep;
import co.orion.onboarding.domain.OnboardingStepCompletedEvent;
import co.orion.practice.domain.PracticeCompletedEvent;
import co.orion.reputation.domain.ReviewCreatedEvent;
import co.orion.scheduling.domain.BookingCompletedEvent;
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
    private final UserRepository users;

    public EngagementListener(AchievementService achievements, UserRepository users) {
        this.achievements = achievements;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LessonCompletedEvent event) {
        seguro(() -> achievements.onLessonCompleted(
                event.studentId(), event.bookingId(), event.completedAt()),
                "clase completada " + event.bookingId());
    }

    /** El cierre manual del profesor. Llega antes que el trabajo horario y cuenta igual. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingCompletedEvent event) {
        seguro(() -> achievements.onAttendanceRecorded(event.bookingId(), event.attended()),
                "asistencia registrada en " + event.bookingId());
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

    /** Una práctica terminada da sus puntos y cuenta para la racha (Bloque 10, Parte B). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PracticeCompletedEvent event) {
        seguro(() -> achievements.onPracticeCompleted(event.studentId(), event.setId(), event.completedAt(),
                        event.perfecta(), event.escuchaAcertada(), event.segundaOportunidad()),
                "práctica terminada " + event.setId());
    }

    /** Calificar la clase da sus puntos y enciende «Primera reseña» en el acto. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ReviewCreatedEvent event) {
        seguro(() -> achievements.onReviewCreated(event.studentId(), event.reviewId(), event.createdAt()),
                "reseña " + event.reviewId());
    }

    /** El primer mensaje del estudiante a cada profe da puntos y enciende «Primer mensaje». */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(MessagePostedEvent event) {
        seguro(() -> achievements.onMessagePosted(event.messageId()), "mensaje " + event.messageId());
    }

    /** El diagnóstico con Meissa da puntos una vez. Solo a estudiantes: son los únicos que los hacen. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AssessmentCompletedEvent event) {
        if (esEstudiante(event.userId())) {
            seguro(() -> achievements.onSomethingHappened(event.userId()), "diagnóstico de " + event.userId());
        }
    }

    /** Terminar el recorrido del estudiante da puntos una vez. Los pasos del profesor no. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(OnboardingStepCompletedEvent event) {
        if (event.step() == OnboardingStep.TOUR_STUDENT) {
            seguro(() -> achievements.onSomethingHappened(event.userId()), "recorrido de " + event.userId());
        }
    }

    private boolean esEstudiante(UUID userId) {
        return users.findById(userId).map(u -> u.getRole() == UserRole.STUDENT).orElse(false);
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
