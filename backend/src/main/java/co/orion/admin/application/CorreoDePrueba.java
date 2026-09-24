package co.orion.admin.application;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.orion.identity.domain.User;
import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;
import co.orion.shared.time.FechasEnPalabras;

/**
 * «Enviarme un correo de prueba» desde Sistema (24/09/2026). La única forma de saber que el correo
 * llega —a Gmail, no a Mailpit— es mandar uno de verdad y mirar la bandeja: por eso sale por el mismo
 * transporte y con la misma plantilla que todos los demás, y si falla se dice por qué.
 */
@Service
public class CorreoDePrueba {

    public record Resultado(boolean enviado, String para, String via, String detalle) {
    }

    private final MailTransport transport;
    private final String via;
    private final Clock clock;

    public CorreoDePrueba(MailTransport transport, @Value("${orion.mail.transport:smtp}") String via, Clock clock) {
        this.transport = transport;
        this.via = via;
        this.clock = clock;
    }

    public Resultado enviar(User admin, String para) {
        String destino = para == null || para.isBlank() ? admin.getEmail() : para.trim();
        String cuando = FechasEnPalabras.dia(clock.instant()) + " a las " + FechasEnPalabras.hora(clock.instant());
        String texto = "Hola:\n\nEste es un correo de prueba de Orión, enviado por " + admin.getFullName() + " el "
                + cuando + " (hora de Colombia) desde Sistema.\n\nSi lo estás leyendo, el correo sale bien.\n\nOrión";
        String html = "<p>Hola:</p><p>Este es un correo de prueba de Orión, enviado desde Sistema el " + cuando
                + " (hora de Colombia).</p><p><strong>Si lo estás leyendo, el correo sale bien.</strong></p><p>Orión</p>";
        try {
            transport.send(OutgoingEmail.plain(destino, "✦ Correo de prueba de Orión", texto, html));
            return new Resultado(true, destino, via, "Salió. Revisa la bandeja (y la carpeta de spam).");
        } catch (Exception ex) {
            return new Resultado(false, destino, via, ex.getMessage() == null ? ex.getClass().getSimpleName()
                    : ex.getMessage());
        }
    }
}
