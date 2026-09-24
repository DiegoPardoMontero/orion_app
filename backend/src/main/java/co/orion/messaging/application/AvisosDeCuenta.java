package co.orion.messaging.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.billing.domain.PayoutPaidEvent;
import co.orion.reputation.domain.SanctionChangedEvent;
import co.orion.shared.time.FechasEnPalabras;
import co.orion.support.domain.SupportAnsweredEvent;

/**
 * Soporte, sanciones y liquidaciones, en la campana (24/09/2026). Una respuesta de soporte que nadie ve no respondió
 * nada, y «toda sanción se le notifica» (V26) era una promesa que el código no cumplía.
 */
@Component
public class AvisosDeCuenta {

    private static final Logger log = LoggerFactory.getLogger(AvisosDeCuenta.class);

    private final NotificationService notifications;

    public AvisosDeCuenta(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(SupportAnsweredEvent event) {
        try {
            notifications.create(event.userId(), "SUPPORT_ANSWERED",
                    "Te respondimos: «" + corto(event.subject(), 90) + "»",
                    "Solicitud " + event.code() + ". Si no quedó resuelto, contéstanos ahí mismo.",
                    "/ayuda/" + event.code());
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar la respuesta de soporte {}: {}", event.code(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(SanctionChangedEvent event) {
        try {
            notifications.create(event.professorId(), event.lifted() ? "SANCTION_LIFTED" : "SANCTION_APPLIED",
                    event.enPalabras(), corto(event.reason(), 380), "/desempeno");
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar la sanción {}: {}", event.sanctionId(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PayoutPaidEvent event) {
        try {
            notifications.create(event.professorId(), "PAYOUT_PAID",
                    "Te pagamos " + FechasEnPalabras.pesos(event.amountCop()),
                    "Por tus clases del " + FechasEnPalabras.periodo(event.periodStart(), event.periodEnd())
                            + ". El detalle está en «Mis ganancias».",
                    "/ganancias");
        } catch (RuntimeException ex) {
            log.warn("No se pudo avisar la liquidación {}: {}", event.payoutId(), ex.getMessage());
        }
    }

    private static String corto(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
