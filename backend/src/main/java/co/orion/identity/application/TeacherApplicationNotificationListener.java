package co.orion.identity.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
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
    /**
     * El correo de quien lleva Orión ({@code ORION_ALERTS_TO}), además de los admins: así ningún
     * profe nuevo se pierde aunque la cuenta de admin tenga otro correo (Pardo, 15/10/2026).
     */
    private final String alertasA;

    public TeacherApplicationNotificationListener(TeacherApplicationMailer mailer, UserRepository users,
                                                  @Value("${orion.alerts.to:}") String alertasA) {
        this.mailer = mailer;
        this.users = users;
        this.alertasA = alertasA == null ? "" : alertasA.trim();
    }

    /** Un profe nuevo abrió su postulación: al equipo, en el acto. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStarted(TeacherApplicationStartedEvent event) {
        List<String> equipo = equipo();
        if (!equipo.isEmpty()) {
            mailer.sendStartedToTeam(equipo, event.applicantName(), event.applicantEmail());
        }
    }

    /** Llegó una postulación (o volvió con cambios): al equipo, para que la revise el mismo día. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(TeacherApplicationSubmittedEvent event) {
        List<String> equipo = equipo();
        if (!equipo.isEmpty()) {
            mailer.sendSubmittedToTeam(equipo, event.applicantName(), event.applicationId(), event.resubmitted());
        }
    }

    /** Los admins activos y el correo de alertas, sin repetir (el admin suele ser el mismo correo). */
    private List<String> equipo() {
        Set<String> correos = new LinkedHashSet<>();
        users.findByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE).forEach(u -> correos.add(u.getEmail().toLowerCase(Locale.ROOT)));
        if (!alertasA.isBlank()) {
            correos.add(alertasA.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(correos);
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
