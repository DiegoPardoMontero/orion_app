package co.orion.messaging.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.application.FinDelFundador;
import co.orion.identity.domain.FounderEndingEvent;

/** El aviso de fin del beneficio de fundador en la campana del profe (brief del profe fundador, paso 4). */
@Component
public class AvisoDeFinDeFundadorEnLaCampana {

    private final NotificationService notifications;

    public AvisoDeFinDeFundadorEnLaCampana(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(FounderEndingEvent event) {
        notifications.create(event.professorId(), "FOUNDER_ENDING", FinDelFundador.TITULO,
                FinDelFundador.texto(event.founderRateBps(), event.baseRateBps(), event.until()), "/ganancias");
    }
}
