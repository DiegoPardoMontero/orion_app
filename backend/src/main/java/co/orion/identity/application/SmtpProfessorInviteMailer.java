package co.orion.identity.application;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

/** Envío real por SMTP del correo de invitación. Un fallo de correo se registra, no rompe el flujo. */
@Component
public class SmtpProfessorInviteMailer implements ProfessorInviteMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpProfessorInviteMailer.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpProfessorInviteMailer(JavaMailSender mailSender,
                                     @Value("${orion.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendInvite(String toEmail, String inviteLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Sofía te invita a enseñar en Orión");

            String text = "Hola,\n\n"
                    + "Sofía te invita a hacer parte del equipo de profesores de Orión. Completa tu"
                    + " perfil en este enlace (vence en 7 días):\n" + inviteLink + "\n\n"
                    + "Nos vemos adentro.\n— El equipo de Orión";
            String html = "<p>Hola,</p>"
                    + "<p>Sofía te invita a hacer parte del equipo de profesores de <strong>Orión</strong>."
                    + " Completa tu perfil para empezar a recibir estudiantes. El enlace vence en 7 días.</p>"
                    + "<p><a href=\"" + escape(inviteLink) + "\">Aceptar la invitación</a></p>"
                    + "<p>Nos vemos adentro.<br>— El equipo de Orión</p>";
            helper.setText(text, html);

            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("No se pudo enviar la invitación a {}: {}", toEmail, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
