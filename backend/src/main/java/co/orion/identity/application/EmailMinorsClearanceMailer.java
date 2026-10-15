package co.orion.identity.application;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;
import co.orion.shared.time.FechasEnPalabras;

/** Con la voz de Orión; un fallo de correo se registra y no rompe nada: la habilitación ya está hecha. */
@Component
public class EmailMinorsClearanceMailer implements MinorsClearanceMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailMinorsClearanceMailer.class);

    private final MailTransport transport;
    private final String baseUrl;

    public EmailMinorsClearanceMailer(MailTransport transport, @Value("${orion.app.base-url}") String baseUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
    }

    @Override
    public void sendGranted(String toEmail, String firstName, LocalDate until) {
        String nombre = firstName == null || firstName.isBlank() ? "" : " " + firstName.trim();
        String hasta = FechasEnPalabras.fecha(until);
        String perfil = baseUrl + "/perfil";
        String subject = "Ya puedes dar clase a adolescentes en Orión";
        String text = "Hola" + nombre + ",\n\n"
                + "El equipo de Orión te habilitó para dar clase a estudiantes de 13 a 17 años. Desde hoy "
                + "apareces en el buscador cuando una familia busca profe para un adolescente, y pueden reservarte.\n\n"
                + "La habilitación vale hasta el " + hasta + ". Recuerda el código de conducta que aceptaste: "
                + "la clase es con el adulto cerca, sin contacto por fuera de Orión y sin grabaciones.\n\n"
                + "Tu perfil: " + perfil + "\n\nOrión";
        String html = "<p>Hola" + escape(nombre) + ",</p>"
                + "<p>El equipo de Orión te habilitó para dar clase a estudiantes de <strong>13 a 17 años</strong>. "
                + "Desde hoy apareces en el buscador cuando una familia busca profe para un adolescente, y pueden reservarte.</p>"
                + "<p>La habilitación vale hasta el <strong>" + escape(hasta) + "</strong>. Recuerda el código de conducta "
                + "que aceptaste: la clase es con el adulto cerca, sin contacto por fuera de Orión y sin grabaciones.</p>"
                + "<p><a href=\"" + perfil + "\">Ver mi perfil</a></p><p>Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(toEmail, subject, text, html));
        } catch (Exception ex) {
            log.error("No se pudo enviar el correo de habilitación para adolescentes a {}", toEmail, ex);
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
