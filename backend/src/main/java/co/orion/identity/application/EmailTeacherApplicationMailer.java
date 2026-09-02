package co.orion.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final String baseUrl;

    public EmailTeacherApplicationMailer(MailTransport transport,
                                         @Value("${orion.app.base-url}") String baseUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
    }

    /**
     * El correo que abre la puerta. Lleva enlace: decirle a alguien "ya puedes publicar tu perfil"
     * y dejarlo buscando dónde es la mitad de un mensaje.
     */
    @Override
    public void sendApproved(String toEmail) {
        String perfil = baseUrl + "/perfil";
        String horarios = baseUrl + "/disponibilidad";

        String text = "¡Felicitaciones! Tu postulación fue aprobada.\n\n"
                + "Te faltan dos pasos para recibir estudiantes:\n"
                + "1. Completa y publica tu perfil: " + perfil + "\n"
                + "2. Marca los horarios en los que puedes dar clase: " + horarios + "\n\n"
                + "Nos vemos adentro.\n— El equipo de Orión";
        String html = "<p>¡Felicitaciones! Tu postulación fue <strong>aprobada</strong>.</p>"
                + "<p>Te faltan dos pasos para recibir estudiantes:</p>"
                + "<ol><li><a href=\"" + perfil + "\">Completa y publica tu perfil</a></li>"
                + "<li><a href=\"" + horarios + "\">Marca los horarios</a> en los que puedes dar clase</li></ol>"
                + "<p>Nos vemos adentro.<br>— El equipo de Orión</p>";
        send(toEmail, "Tu postulación en Orión fue aprobada", text, html);
    }

    @Override
    public void sendChangesRequested(String toEmail, String note) {
        String aplicacion = baseUrl + "/aplicacion";

        String text = "Revisamos tu postulación y necesitamos algunos ajustes antes de aprobarla:\n\n"
                + note + "\n\n"
                + "Actualízala y vuélvela a enviar cuando estés listo: " + aplicacion
                + "\n— El equipo de Orión";
        String html = "<p>Revisamos tu postulación y necesitamos algunos ajustes antes de aprobarla:</p>"
                + "<blockquote>" + escape(note) + "</blockquote>"
                + "<p><a href=\"" + aplicacion + "\">Actualiza tu postulación</a> y vuélvela a enviar "
                + "cuando estés listo.</p>"
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
