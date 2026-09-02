package co.orion.messaging.application;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.orion.messaging.application.MessageDelivery.Delivery;
import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/**
 * Convierte un mensaje nuevo en un aviso a la contraparte: notificación in-app + correo.
 *
 * AFTER_COMMIT y @Async, con la misma lógica que las reservas: nunca se avisa un mensaje que hizo
 * rollback, y un fallo de correo (try/catch + log) no revierte nada. La notificación in-app y el
 * sello notified_at se persisten en {@link MessageDelivery} (transacción propia, idempotente); el
 * correo es el efecto externo, deseable pero no imprescindible.
 */
@Component
public class MessageNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(MessageNotificationListener.class);

    private final MessageDelivery delivery;
    private final MailTransport mail;
    private final Clock clock;

    public MessageNotificationListener(MessageDelivery delivery, MailTransport mail, Clock clock) {
        this.delivery = delivery;
        this.mail = mail;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessagePosted(MessagePostedEvent event) {
        delivery.deliver(event.messageId(), clock.instant()).ifPresent(this::email);
    }

    private void email(Delivery target) {
        String firstName = target.recipientName().split(" ")[0];
        String subject = "Nuevo mensaje de " + target.senderName() + " en Orión";
        String html = """
                <p>Hola, %s.</p>
                <p>%s te escribió un mensaje en Orión. Respóndele desde la plataforma, en tu bandeja
                de Mensajes.</p>
                <p>Un abrazo,<br>El equipo de Orión</p>
                """.formatted(firstName, target.senderName());
        String text = """
                Hola, %s.

                %s te escribió un mensaje en Orión. Respóndele desde la plataforma, en tu bandeja de Mensajes.

                Un abrazo,
                El equipo de Orión
                """.formatted(firstName, target.senderName());
        try {
            mail.send(OutgoingEmail.plain(target.recipientEmail(), subject, text, html));
            log.info("Aviso de mensaje enviado a {}", target.recipientEmail());
        } catch (Exception ex) {
            log.error("No se pudo enviar el aviso de mensaje a {}. La notificación in-app ya quedó.",
                    target.recipientEmail(), ex);
        }
    }
}
