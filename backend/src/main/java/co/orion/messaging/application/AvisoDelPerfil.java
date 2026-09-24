package co.orion.messaging.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.scheduling.application.RecordatorioDelPerfil;
import co.orion.scheduling.domain.PerfilPorCompletarEvent;

/** Los recordatorios del perfil del profesor que van a la campana: el del día 1 y el del día 3. */
@Component
public class AvisoDelPerfil {

    private final NotificationService notifications;

    public AvisoDelPerfil(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PerfilPorCompletarEvent event) {
        if (event.paso() == 2) {
            return;
        }
        String faltan = String.join(", ", RecordatorioDelPerfil.enPalabras(event.faltan()));
        boolean yaRecibe = RecordatorioDelPerfil.yaRecibe(event.faltan());
        String titulo = yaRecibe
                ? "Rigel: termina tu perfil"
                : event.paso() == 1
                        ? "Rigel: tu perfil está casi listo para recibir estudiantes"
                        : "Tu perfil todavía no recibe estudiantes";
        String cuerpo = yaRecibe
                ? "Te falta " + faltan + ". Un perfil completo da confianza y recibe más reservas."
                : "Te falta " + faltan + ". Completo y publicado, apareces en el buscador.";
        notifications.create(event.professorId(), "PROFILE_NUDGE", titulo, cuerpo,
                RecordatorioDelPerfil.rutaPara(event.faltan()));
    }
}
