package co.orion.identity.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Convierte una decisión de postulación en un correo, AFTER_COMMIT: si la transacción hace rollback
 * el aspirante no recibe un aviso falso, y un fallo de correo (el mailer lo traga) no toca la decisión.
 */
@Component
public class TeacherApplicationNotificationListener {

    private final TeacherApplicationMailer mailer;

    public TeacherApplicationNotificationListener(TeacherApplicationMailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(TeacherApplicationDecidedEvent event) {
        switch (event.decision()) {
            case APPROVED -> mailer.sendApproved(event.toEmail());
            case CHANGES_REQUESTED -> mailer.sendChangesRequested(event.toEmail(), event.note());
            case REJECTED -> mailer.sendRejected(event.toEmail(), event.note());
        }
    }
}
