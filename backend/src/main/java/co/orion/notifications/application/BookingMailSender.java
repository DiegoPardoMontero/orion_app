package co.orion.notifications.application;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

@Component
public class BookingMailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public BookingMailSender(JavaMailSender mailSender,
                             @Value("${orion.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(BookingEmail email) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        // multipart = true porque puede llevar adjunto; el HTML va con alternativa de texto plano
        // para los clientes que no lo renderizan.
        MimeMessageHelper helper = new MimeMessageHelper(
                message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

        helper.setFrom(from);
        helper.setTo(email.to());
        helper.setSubject(email.subject());
        helper.setText(email.text(), email.html());

        if (email.ics() != null) {
            helper.addAttachment("clase-orion.ics",
                    new ByteArrayResource(email.ics().getBytes(StandardCharsets.UTF_8)),
                    "text/calendar");
        }

        mailSender.send(message);
    }
}
