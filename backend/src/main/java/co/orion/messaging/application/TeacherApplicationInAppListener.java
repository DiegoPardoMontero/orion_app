package co.orion.messaging.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.application.TeacherApplicationStartedEvent;
import co.orion.identity.application.TeacherApplicationSubmittedEvent;
import co.orion.identity.domain.UserRole;
import co.orion.identity.domain.UserStatus;
import co.orion.identity.persistence.UserRepository;

/**
 * La campana del admin cuando un profe nuevo se registra y cuando envía su postulación (Pardo,
 * 15/10/2026: «para no perder ninguno»). El correo al equipo sale de {@code identity}; esto es el
 * aviso dentro de Orión, que se ve en cuanto el admin entra. {@code AFTER_COMMIT} + {@code REQUIRES_NEW}
 * y un fallo tragado, como los demás avisos: una notificación nunca tumba una postulación.
 */
@Component
public class TeacherApplicationInAppListener {

    private static final Logger log = LoggerFactory.getLogger(TeacherApplicationInAppListener.class);

    private final NotificationService notifications;
    private final UserRepository users;

    public TeacherApplicationInAppListener(NotificationService notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStarted(TeacherApplicationStartedEvent event) {
        String nombre = nombre(event.applicantName());
        aLosAdmins("TEACHER_APPLICANT_REGISTERED", "Profe nuevo: " + nombre,
                nombre + " (" + event.applicantEmail() + ") creó su cuenta y abrió su postulación. Todavía no la envía.",
                "/admin/usuarios");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubmitted(TeacherApplicationSubmittedEvent event) {
        String nombre = nombre(event.applicantName());
        aLosAdmins("TEACHER_APPLICATION_SUBMITTED",
                event.resubmitted() ? nombre + " reenvió su postulación" : "Nueva postulación de " + nombre,
                event.resubmitted() ? "Hizo los cambios que pediste. Revísala hoy." : "Cuanto antes la revises, antes puede dar clases.",
                "/admin/aplicaciones/" + event.applicationId());
    }

    private void aLosAdmins(String tipo, String titulo, String cuerpo, String ruta) {
        try {
            users.findByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE).forEach(admin ->
                    notifications.create(admin.getId(), tipo, titulo, cuerpo, ruta));
        } catch (RuntimeException ex) {
            log.error("No se pudo avisar al admin de «{}». La postulación no se ve afectada.", titulo, ex);
        }
    }

    private static String nombre(String applicantName) {
        return applicantName == null || applicantName.isBlank() ? "Un profe nuevo" : applicantName.trim();
    }
}
