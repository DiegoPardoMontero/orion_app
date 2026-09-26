package co.orion.messaging.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.identity.application.FinDelFundador;
import co.orion.identity.domain.FounderEndingEvent;

/**
 * El aviso de fin del beneficio de fundador en la campana del profe (brief del profe fundador, paso 4).
 *
 * <p>Antes del commit y no después: el aviso entra en la misma transacción que marca al profe como
 * avisado. Si guardarlo falla, se deshacen las dos cosas y la siguiente corrida lo vuelve a intentar;
 * después del commit, la marca ya habría quedado puesta y el aviso se perdería para siempre.
 */
@Component
public class AvisoDeFinDeFundadorEnLaCampana {

    private final NotificationService notifications;

    public AvisoDeFinDeFundadorEnLaCampana(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(FounderEndingEvent event) {
        notifications.create(event.professorId(), "FOUNDER_ENDING", FinDelFundador.TITULO,
                FinDelFundador.texto(event.founderRateBps(), event.baseRateBps(), event.until()), "/ganancias");
    }
}
