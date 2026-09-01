package co.orion.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/** Compone el correo de recuperación y lo entrega al transporte activo. Un fallo se registra y se traga. */
@Component
public class EmailPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailPasswordResetMailer.class);

    private final MailTransport transport;

    public EmailPasswordResetMailer(MailTransport transport) {
        this.transport = transport;
    }

    @Override
    public void sendResetLink(String toEmail, String fullName, String resetLink) {
        String text = "Hola " + fullName + ",\n\n"
                + "Pediste restablecer tu contraseña. Abre este enlace (vence en 30 minutos):\n"
                + resetLink + "\n\n"
                + "Si no fuiste tú, ignora este correo: tu contraseña sigue igual.\n\n— Orión";
        String html = "<p>Hola " + escape(fullName) + ",</p>"
                + "<p>Pediste restablecer tu contraseña. El enlace vence en 30 minutos.</p>"
                + "<p><a href=\"" + escape(resetLink) + "\">Restablecer mi contraseña</a></p>"
                + "<p>Si no fuiste tú, ignora este correo: tu contraseña sigue igual.</p><p>— Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(toEmail, "Recupera tu contraseña de Orión", text, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar el correo de recuperación a {}: {}", toEmail, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
