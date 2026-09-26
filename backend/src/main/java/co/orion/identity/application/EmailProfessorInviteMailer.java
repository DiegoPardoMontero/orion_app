package co.orion.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/**
 * Compone la invitación y la entrega al transporte activo. Un fallo de correo se registra, no rompe.
 *
 * <p>La firma es «El equipo de Orión», siempre: el nombre de quien invita no sale en ningún texto para
 * usuarios (Pardo, 26/09/2026). El profe trata con Orión.
 */
@Component
public class EmailProfessorInviteMailer implements ProfessorInviteMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailProfessorInviteMailer.class);

    private final MailTransport transport;

    public EmailProfessorInviteMailer(MailTransport transport) {
        this.transport = transport;
    }

    @Override
    public void sendInvite(String toEmail, String professorName, String inviteLink) {
        String saludo = professorName == null || professorName.isBlank() ? "Hola" : "Hola, " + professorName.trim();
        String text = saludo + ",\n\n"
                + "El equipo de Orión te invita a ser de sus primeros profes. Acepta la invitación en"
                + " este enlace (vence en 7 días):\n" + inviteLink + "\n\n"
                + "Nos vemos adentro.\nEl equipo de Orión";
        String html = "<p>" + escape(saludo) + ",</p>"
                + "<p>El equipo de <strong>Orión</strong> te invita a ser de sus primeros profes."
                + " El enlace es solo para ti y vence en 7 días.</p>"
                + "<p><a href=\"" + escape(inviteLink) + "\">Ver la invitación</a></p>"
                + "<p>Nos vemos adentro.<br>El equipo de Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(toEmail, "Orión te invita a enseñar", text, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar la invitación a {}: {}", toEmail, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
