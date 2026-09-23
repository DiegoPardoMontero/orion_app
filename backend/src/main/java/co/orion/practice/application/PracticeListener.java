package co.orion.practice.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.teaching.domain.LessonNotePublishedEvent;

/**
 * Publicar un acta encola su práctica. Solo la primera publicación: una corrección posterior no
 * crea otro set. Un fallo aquí se registra y se traga: el acta ya se publicó y eso no se deshace.
 */
@Component
public class PracticeListener {

    private static final Logger log = LoggerFactory.getLogger(PracticeListener.class);

    private final PracticeService practica;

    public PracticeListener(PracticeService practica) {
        this.practica = practica;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LessonNotePublishedEvent e) {
        if (e.actualizacion()) {
            return;
        }
        try {
            List<Material.Termino> terminos = e.vocabulario().stream()
                    .map(t -> new Material.Termino(t.term(), t.meaning())).toList();
            practica.crearDesdeActa(e.noteId(), e.studentId(), e.professorId(), new Material(e.languageCode(),
                    e.workedOn(), e.recurringIssues(), e.nextSteps(), terminos, e.bookingId().toString(),
                    e.claseEmpieza() == null ? null : e.claseEmpieza().toString()));
        } catch (RuntimeException ex) {
            log.error("No se pudo encolar la práctica del acta {}. El acta sigue publicada.", e.noteId(), ex);
        }
    }
}
