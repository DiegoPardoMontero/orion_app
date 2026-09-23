package co.orion.messaging.application;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;
import co.orion.teaching.domain.LessonNoteNudgeEvent;
import co.orion.teaching.domain.LessonNotePublishedEvent;

/**
 * Los avisos del acta de clase (Bloque 10), in-app y <strong>sin correo</strong>: un correo por
 * acta es cómo se enseña a la gente a ignorar los correos.
 *
 * <p>Al estudiante, cuando su profesor publica el acta o la actualiza. Al profesor, el único
 * recordatorio de escribirla. {@code AFTER_COMMIT} + {@code REQUIRES_NEW} como el resto: nunca se
 * avisa de algo que hizo rollback, y un fallo aquí no tumba la publicación.
 *
 * <p>Este módulo importa los eventos de {@code teaching} y nada más de él: es la misma arista que
 * ya tiene con los eventos del ciclo de vida.
 */
@Component
public class LessonNoteNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(LessonNoteNotificationListener.class);
    private static final DateTimeFormatter DIA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO"));

    private final NotificationService notifications;
    private final BookingRepository bookings;
    private final UserRepository users;

    public LessonNoteNotificationListener(NotificationService notifications, BookingRepository bookings,
                                          UserRepository users) {
        this.notifications = notifications;
        this.bookings = bookings;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPublished(LessonNotePublishedEvent e) {
        safely(() -> bookings.findById(e.bookingId()).ifPresent(b -> {
            String profe = primerNombre(e.professorId());
            String dia = b.getStartsAt().atZone(BusinessZone.BOGOTA).format(DIA);
            notifications.create(e.studentId(),
                    e.actualizacion() ? "LESSON_NOTE_UPDATED" : "LESSON_NOTE_PUBLISHED",
                    e.actualizacion() ? profe + " actualizó el resumen de tu clase"
                            : profe + " publicó el resumen de tu clase",
                    "El resumen de tu clase del " + dia + " ya está disponible.",
                    "/mis-clases/" + e.bookingId() + "/acta");
        }));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNudge(LessonNoteNudgeEvent e) {
        safely(() -> notifications.create(e.professorId(), "LESSON_NOTE_NUDGE",
                "¿Nos cuentas cómo estuvo tu clase con " + primerNombre(e.studentId()) + "?",
                "Escribe lo que se te venga a la cabeza: nosotros le damos forma.",
                "/mis-clases/" + e.bookingId() + "/acta"));
    }

    private String primerNombre(UUID userId) {
        return users.findById(userId).map(u -> {
            String n = u.getFullName() == null ? "" : u.getFullName().trim();
            int espacio = n.indexOf(' ');
            return espacio < 0 ? n : n.substring(0, espacio);
        }).orElse("Tu profesor");
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.error("No se pudo crear un aviso del acta de clase", ex);
        }
    }
}
