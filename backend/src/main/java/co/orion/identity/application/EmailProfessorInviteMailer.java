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
    public void sendInvite(String toEmail, String professorName, String inviterName, String inviteLink) {
        String saludo = professorName == null || professorName.isBlank() ? "Hola" : "Hola, " + professorName.trim();
        String quien = inviterName == null || inviterName.isBlank() ? "El equipo de Orión" : inviterName.trim();
        String text = saludo + ",\n\n"
                + quien + " te invita a ser de los primeros profes de Orión. Acepta la invitación en"
                + " este enlace (vence en 7 días):\n" + inviteLink + "\n\n"
                + "Nos vemos adentro.\nEl equipo de Orión";
        String html = "<p>" + escape(saludo) + ",</p>"
                + "<p>" + escape(quien) + " te invita a ser de los primeros profes de <strong>Orión</strong>."
                + " El enlace es solo para ti y vence en 7 días.</p>"
                + "<p><a href=\"" + escape(inviteLink) + "\">Ver la invitación</a></p>"
                + "<p>Nos vemos adentro.<br>El equipo de Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(toEmail, quien + " te invita a enseñar en Orión", text, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar la invitación a {}: {}", toEmail, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
