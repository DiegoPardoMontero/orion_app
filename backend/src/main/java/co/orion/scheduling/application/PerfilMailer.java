package co.orion.scheduling.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;

/**
 * El correo del segundo día al profesor que todavía no recibe estudiantes: qué le falta y por qué
 * importa. Un fallo se registra y se traga: la franja en la app sigue ahí.
 */
@Component
public class PerfilMailer {

    private static final Logger log = LoggerFactory.getLogger(PerfilMailer.class);

    private final MailTransport transport;
    private final String baseUrl;

    public PerfilMailer(MailTransport transport, @Value("${orion.app.base-url}") String baseUrl) {
        this.transport = transport;
        this.baseUrl = baseUrl;
    }

    /** @param yaRecibe publicado y con horarios: lo que falta lo mejora, no lo bloquea */
    public void recordar(String correo, String nombre, List<String> faltan, String ruta, boolean yaRecibe) {
        String primer = nombre == null ? "" : nombre.trim().split("\\s+")[0];
        String enlace = baseUrl + ruta;
        String lista = String.join(", ", faltan);
        String estado = yaRecibe ? "A tu perfil le falta poco" : "Tu perfil todavía no recibe estudiantes";
        String porque = yaRecibe
                ? "Un perfil completo da confianza: los estudiantes reservan más con quien muestran completo."
                : "Con tus horarios abiertos y tu perfil completo y publicado, apareces en el buscador y los "
                        + "estudiantes pueden reservar contigo.";
        String texto = "Hola, " + primer + ":\n\n"
                + "Soy Rigel, de Orión. " + estado + ": te falta " + lista + ".\n\n"
                + porque + "\n\n"
                + "Termínalo aquí: " + enlace + "\n\nOrión";
        String html = "<p>Hola, " + escape(primer) + ":</p>"
                + "<p>Soy Rigel, de Orión. " + estado + ": te falta <strong>"
                + escape(lista) + "</strong>.</p>"
                + "<p>" + escape(porque) + "</p>"
                + "<p><a href=\"" + escape(enlace) + "\">Terminar mi perfil</a></p><p>Orión</p>";
        String asunto = yaRecibe ? "⭐ Termina tu perfil en Orión" : "⭐ Tu perfil está a un paso de recibir estudiantes";
        try {
            transport.send(OutgoingEmail.plain(correo, asunto, texto, html));
        } catch (Exception ex) {
            log.warn("No se pudo enviar el recordatorio del perfil a {}: {}", correo, ex.getMessage());
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
