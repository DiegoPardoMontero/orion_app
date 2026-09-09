package co.orion.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/**
 * Compone el correo de verificación y lo entrega al transporte activo, que le pone la marca.
 * Un fallo se registra y se traga: la cuenta ya existe y hay un botón de reenviar.
 */
@Component
public class EmailVerificationLinkMailer implements EmailVerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationLinkMailer.class);

    private final MailTransport transport;

    public EmailVerificationLinkMailer(MailTransport transport) {
        this.transport = transport;
    }

    @Override
    public void sendVerificationLink(String toEmail, String fullName, String verificationLink) {
        String text = "Hola " + fullName + ",\n\n"
                + "Confirma que este correo es tuyo para poder reservar clases en Orión. "
                + "Abre este enlace (vence en 24 horas):\n"
                + verificationLink + "\n\n"
                + "Si no creaste una cuenta en Orión, ignora este correo.\n\n— Orión";
        String html = "<p>Hola " + escape(fullName) + ",</p>"
                + "<p>Confirma que este correo es tuyo para poder reservar clases en Orión. "
                + "El enlace vence en 24 horas.</p>"
                + "<p><a href=\"" + escape(verificationLink) + "\">Confirmar mi correo</a></p>"
                + "<p>Si no creaste una cuenta en Orión, ignora este correo.</p><p>— Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(toEmail, "Confirma tu correo en Orión", text, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar la verificación a {}: {}", toEmail, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
