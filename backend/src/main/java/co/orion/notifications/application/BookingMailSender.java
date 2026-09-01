package co.orion.notifications.application;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/**
 * Traduce un {@link BookingEmail} a un {@link OutgoingEmail} y lo entrega al transporte activo
 * (SMTP en local, API HTTP de Resend en producción). El {@code .ics} viaja como adjunto.
 */
@Component
public class BookingMailSender {

    private final MailTransport transport;

    public BookingMailSender(MailTransport transport) {
        this.transport = transport;
    }

    public void send(BookingEmail email) {
        OutgoingEmail outgoing = email.ics() == null
                ? OutgoingEmail.plain(email.to(), email.subject(), email.text(), email.html())
                : OutgoingEmail.withAttachment(email.to(), email.subject(), email.text(), email.html(),
                        "clase-orion.ics", email.ics().getBytes(StandardCharsets.UTF_8), "text/calendar");
        transport.send(outgoing);
    }
}
