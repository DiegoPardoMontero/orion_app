package co.orion.identity.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** El correo al profe habilitado, AFTER_COMMIT: nunca por una habilitación que hizo rollback. */
@Component
public class MinorsClearanceNotificationListener {

    private final MinorsClearanceMailer mailer;

    public MinorsClearanceNotificationListener(MinorsClearanceMailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGranted(MinorsClearanceGrantedEvent event) {
        mailer.sendGranted(event.email(), event.firstName(), event.until());
    }
}
