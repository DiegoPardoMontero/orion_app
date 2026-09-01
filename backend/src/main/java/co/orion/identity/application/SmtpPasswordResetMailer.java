package co.orion.identity.application;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

/** Envío real por SMTP. Un fallo de correo se registra y se traga: nunca rompe (ni delata) el flujo. */
@Component
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetMailer(JavaMailSender mailSender,
                                   @Value("${orion.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendResetLink(String toEmail, String fullName, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Recupera tu contraseña de Orión");

            String text = "Hola " + fullName + ",\n\n"
                    + "Pediste restablecer tu contraseña. Abre este enlace (vence en 30 minutos):\n"
                    + resetLink + "\n\n"
                    + "Si no fuiste tú, ignora este correo: tu contraseña sigue igual.\n\n— Orión";
            String html = "<p>Hola " + escape(fullName) + ",</p>"
                    + "<p>Pediste restablecer tu contraseña. El enlace vence en 30 minutos.</p>"
                    + "<p><a href=\"" + escape(resetLink) + "\">Restablecer mi contraseña</a></p>"
                    + "<p>Si no fuiste tú, ignora este correo: tu contraseña sigue igual.</p><p>— Orión</p>";
            helper.setText(text, html);

            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("No se pudo enviar el correo de recuperación a {}: {}", toEmail, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
