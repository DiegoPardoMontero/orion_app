package co.orion.shared.mail;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

/**
 * Transporte por SMTP (Mailpit en local). Activo salvo que se pida `resend`. Multipart siempre:
 * {@code setText(plano, html)} lo exige, y sin él un correo con alternativa de texto lanzaría
 * "Not in multipart mode" y no saldría.
 */
@Component
@Qualifier("entrega")
@ConditionalOnProperty(name = "orion.mail.transport", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailTransport(JavaMailSender mailSender, @Value("${orion.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(OutgoingEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.textBody(), email.htmlBody());

            if (email.hasAttachment()) {
                helper.addAttachment(email.attachmentFilename(),
                        new ByteArrayResource(email.attachmentContent()),
                        email.attachmentContentType());
            }

            mailSender.send(message);
        } catch (Exception ex) {
            // Se re-lanza para que quien envía decida (los mailers lo tragan y registran; el listener
            // de reservas también). Un fallo de correo nunca debe romper la operación de negocio.
            throw new MailDeliveryException("No se pudo enviar el correo por SMTP a " + email.to(), ex);
        }
    }
}
