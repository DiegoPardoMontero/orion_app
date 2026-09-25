package co.orion.identity.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/** El correo del aviso de fin del beneficio de fundador. Un fallo se registra: la campana sigue. */
@Component
public class FounderMailer {

    private static final Logger log = LoggerFactory.getLogger(FounderMailer.class);

    private final MailTransport transport;
    private final String baseUrl;

    public FounderMailer(MailTransport transport, @Value("${orion.app.base-url}") String baseUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
    }

    public void avisarFin(String correo, String nombre, int founderRateBps, int baseRateBps, Instant until) {
        String primer = nombre == null ? "" : nombre.trim().split("\\s+")[0];
        String cuerpo = FinDelFundador.texto(founderRateBps, baseRateBps, until);
        String enlace = baseUrl + "/ganancias";
        String texto = "Hola, " + primer + ":\n\n" + cuerpo + "\n\nTus ganancias: " + enlace + "\n\nOrión";
        String html = "<p>Hola, " + escape(primer) + ":</p><p>" + escape(cuerpo) + "</p>"
                + "<p><a href=\"" + escape(enlace) + "\">Ver mis ganancias</a></p><p>Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(correo, FinDelFundador.TITULO, texto, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar el aviso de fin de fundador a {}: {}", correo, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
