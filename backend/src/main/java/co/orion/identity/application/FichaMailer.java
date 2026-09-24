package co.orion.identity.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/**
 * El correo del segundo día: «Rigel te guardó un lugar». Corto, con lo que falta y el logro que se
 * gana. Un fallo se registra y se traga: la ficha sigue ahí, y la franja en la app también.
 */
@Component
public class FichaMailer {

    private static final Logger log = LoggerFactory.getLogger(FichaMailer.class);

    private final MailTransport transport;
    private final String baseUrl;

    public FichaMailer(MailTransport transport, @Value("${orion.app.base-url}") String baseUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
    }

    public void recordar(String correo, String nombre, List<String> faltan) {
        String primer = nombre == null ? "" : nombre.trim().split("\\s+")[0];
        String enlace = baseUrl + "/cuenta?seccion=ficha";
        String lista = String.join(", ", faltan);
        String texto = "Hola, " + primer + ":\n\n"
                + "Soy Rigel, de Orión. Tu ficha está a medias: te falta " + lista + ".\n\n"
                + "Con tu ficha completa —y visible, te lo recomiendo— los profes llegan a tu clase sabiendo qué "
                + "buscas, y te llevas el logro «Ficha completa» (+25 puntos).\n\n"
                + "Complétala aquí, es un minuto: " + enlace + "\n\nOrión";
        String html = "<p>Hola, " + escape(primer) + ":</p>"
                + "<p>Soy Rigel, de Orión. Tu ficha está a medias: te falta <strong>" + escape(lista) + "</strong>.</p>"
                + "<p>Con tu ficha completa —y visible, te lo recomiendo— los profes llegan a tu clase sabiendo qué "
                + "buscas, y te llevas el logro <strong>«Ficha completa»</strong> (+25 puntos).</p>"
                + "<p><a href=\"" + escape(enlace) + "\">Completar mi ficha</a> · es un minuto.</p><p>Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(correo, "⭐ Tu ficha está a un minuto de estar completa", texto, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar el recordatorio de la ficha a {}: {}", correo, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
