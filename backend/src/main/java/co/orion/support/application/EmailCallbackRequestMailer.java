package co.orion.support.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;
import co.orion.support.domain.CallbackRequest;

/**
 * El aviso va al correo público de la academia, el mismo que atiende los derechos de datos: es
 * quien habla con la gente. Un fallo se registra y se traga — el pedido ya quedó guardado y se ve en
 * Administración → Llamadas aunque el correo no salga.
 */
@Component
public class EmailCallbackRequestMailer implements CallbackRequestMailer {

    private static final Logger log = LoggerFactory.getLogger(EmailCallbackRequestMailer.class);

    private final MailTransport transport;
    private final String destino;
    private final String baseUrl;

    public EmailCallbackRequestMailer(MailTransport transport,
                                      @Value("${orion.legal.correo}") String destino,
                                      @Value("${orion.app.base-url}") String baseUrl) {
        this.transport = transport;
        this.destino = destino;
        this.baseUrl = baseUrl;
    }

    @Override
    public void avisar(CallbackRequest solicitud) {
        String enlace = baseUrl + "/admin/llamadas";
        String asunto = solicitud.getFirstName() + " quiere que le escribamos";
        String texto = solicitud.getFirstName() + " prefiere que una persona le escriba antes de hacer "
                + "el diagnóstico.\n\nWhatsApp: " + solicitud.getWhatsapp()
                + "\n\nMárcalo como atendido en " + enlace + "\n\nOrión";
        String html = "<p><strong>" + escape(solicitud.getFirstName()) + "</strong> prefiere que una "
                + "persona le escriba antes de hacer el diagnóstico.</p>"
                + "<p>WhatsApp: <strong>" + escape(solicitud.getWhatsapp()) + "</strong></p>"
                + "<p><a href=\"" + escape(enlace) + "\">Verlo en Administración → Llamadas</a></p>";
        try {
            transport.send(OutgoingEmail.plain(destino, asunto, texto, html));
        } catch (Exception ex) {
            log.warn("No se pudo avisar de la solicitud de llamada {}: {}", solicitud.getId(), ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
