package co.orion.identity.application;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.domain.UserStatus;
import co.orion.identity.persistence.UserRepository;

/**
 * Convierte una postulación enviada, o una decisión sobre ella, en un correo, AFTER_COMMIT: si la transacción hace rollback
 * el aspirante no recibe un aviso falso, y un fallo de correo (el mailer lo traga) no toca la decisión.
 */
@Component
public class TeacherApplicationNotificationListener {

    private final TeacherApplicationMailer mailer;
    private final UserRepository users;

    public TeacherApplicationNotificationListener(TeacherApplicationMailer mailer, UserRepository users) {
        this.mailer = mailer;
        this.users = users;
    }

    /** Llegó una postulación (o volvió con cambios): al equipo, para que la revise el mismo día. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(TeacherApplicationSubmittedEvent event) {
        List<String> equipo = users.findByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE).stream()
                .map(User::getEmail).toList();
        if (!equipo.isEmpty()) {
            mailer.sendSubmittedToTeam(equipo, event.applicantName(), event.applicationId(), event.resubmitted());
        }
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
