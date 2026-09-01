package co.orion.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/** Compone la invitación y la entrega al transporte activo. Un fallo de correo se registra, no rompe. */
@Component
public class EmailProfessorInviteMailer implements ProfessorInviteMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailProfessorInviteMailer.class);

    private final MailTransport transport;

    public EmailProfessorInviteMailer(MailTransport transport) {
        this.transport = transport;
    }

    @Override
    public void sendInvite(String toEmail, String inviteLink) {
        String text = "Hola,\n\n"
                + "Sofía te invita a hacer parte del equipo de profesores de Orión. Completa tu"
                + " perfil en este enlace (vence en 7 días):\n" + inviteLink + "\n\n"
                + "Nos vemos adentro.\n— El equipo de Orión";
        String html = "<p>Hola,</p>"
                + "<p>Sofía te invita a hacer parte del equipo de profesores de <strong>Orión</strong>."
                + " Completa tu perfil para empezar a recibir estudiantes. El enlace vence en 7 días.</p>"
                + "<p><a href=\"" + escape(inviteLink) + "\">Aceptar la invitación</a></p>"
                + "<p>Nos vemos adentro.<br>— El equipo de Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(toEmail, "Sofía te invita a enseñar en Orión", text, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar la invitación a {}: {}", toEmail, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
