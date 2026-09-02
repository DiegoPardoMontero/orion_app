package co.orion.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/**
 * Compone y entrega los correos de decisión de una postulación con la voz de Orión. Un fallo de
 * correo se registra, nunca rompe: la decisión ya está tomada y guardada.
 */
@Component
public class EmailTeacherApplicationMailer implements TeacherApplicationMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailTeacherApplicationMailer.class);

    private final MailTransport transport;

    public EmailTeacherApplicationMailer(MailTransport transport) {
        this.transport = transport;
    }

    @Override
    public void sendApproved(String toEmail) {
        String text = "¡Felicitaciones! Tu postulación fue aprobada.\n\n"
                + "Ya puedes publicar tu perfil y empezar a recibir estudiantes en Orión.\n\n"
                + "Nos vemos adentro.\n— El equipo de Orión";
        String html = "<p>¡Felicitaciones! Tu postulación fue <strong>aprobada</strong>.</p>"
                + "<p>Ya puedes publicar tu perfil y empezar a recibir estudiantes en Orión.</p>"
                + "<p>Nos vemos adentro.<br>— El equipo de Orión</p>";
        send(toEmail, "Tu postulación en Orión fue aprobada", text, html);
    }

    @Override
    public void sendChangesRequested(String toEmail, String note) {
        String text = "Revisamos tu postulación y necesitamos algunos ajustes antes de aprobarla:\n\n"
                + note + "\n\n"
                + "Actualiza tu postulación y vuélvela a enviar cuando estés listo.\n— El equipo de Orión";
        String html = "<p>Revisamos tu postulación y necesitamos algunos ajustes antes de aprobarla:</p>"
                + "<blockquote>" + escape(note) + "</blockquote>"
                + "<p>Actualiza tu postulación y vuélvela a enviar cuando estés listo.</p>"
                + "<p>— El equipo de Orión</p>";
        send(toEmail, "Tu postulación en Orión necesita cambios", text, html);
    }

    @Override
    public void sendRejected(String toEmail, String note) {
        String text = "Gracias por tu interés en enseñar en Orión.\n\n"
                + "Por ahora no podemos aprobar tu postulación:\n\n" + note + "\n\n"
                + "Te deseamos lo mejor.\n— El equipo de Orión";
        String html = "<p>Gracias por tu interés en enseñar en Orión.</p>"
                + "<p>Por ahora no podemos aprobar tu postulación:</p>"
                + "<blockquote>" + escape(note) + "</blockquote>"
                + "<p>Te deseamos lo mejor.<br>— El equipo de Orión</p>";
        send(toEmail, "Sobre tu postulación en Orión", text, html);
    }

    private void send(String toEmail, String subject, String text, String html) {
        try {
            transport.send(OutgoingEmail.plain(toEmail, subject, text, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar el correo de postulación a {}: {}", toEmail, ex.getMessage());
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
