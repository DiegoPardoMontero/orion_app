package co.orion.lifecycle.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;
import co.orion.shared.time.BusinessZone;

/**
 * Los dos correos del retracto: al aceptarlo y al confirmar la devolución.
 *
 * <p>Son dos y no uno porque entre ellos pueden pasar quince días. Sin el primero, el estudiante no
 * sabe si su retracto se registró; sin el segundo, no sabe que ya le llegó el dinero y escribe a
 * preguntar — que es justo el trabajo manual que este bloque intenta quitarte.
 */
@Component
public class RetractionMailer {

    private static final Logger log = LoggerFactory.getLogger(RetractionMailer.class);

    private final MailTransport transport;
    private final UserRepository users;

    public RetractionMailer(MailTransport transport, UserRepository users) {
        this.transport = transport;
        this.users = users;
    }

    public void confirmarRetracto(UUID studentId, long montoCop, Instant vence) {
        users.findById(studentId).ifPresent(user -> {
            String fecha = LocalDate.ofInstant(vence, BusinessZone.BOGOTA).toString();
            String texto = "Hola " + user.getFullName() + ",\n\n"
                    + "Registramos tu retracto. Cancelamos la clase y te vamos a devolver "
                    + formato(montoCop) + " al mismo medio de pago que usaste.\n\n"
                    + "El plazo legal para hacerlo vence el " + fecha + ". Normalmente es antes.\n\n"
                    + "No tienes que hacer nada más.\n\nOrión";
            enviar(user, "Tu retracto quedó registrado", texto);
        });
    }

    public void confirmarDevolucion(UUID studentId, long montoCop, String referencia) {
        users.findById(studentId).ifPresent(user -> {
            String texto = "Hola " + user.getFullName() + ",\n\n"
                    + "Ya devolvimos " + formato(montoCop) + " a tu medio de pago.\n"
                    + "Referencia: " + referencia + "\n\n"
                    + "Según tu banco, puede tardar unos días en reflejarse.\n\nOrión";
            enviar(user, "Devolvimos tu dinero", texto);
        });
    }

    private void enviar(User user, String asunto, String texto) {
        try {
            transport.send(OutgoingEmail.plain(user.getEmail(), asunto, texto,
                    "<p>" + texto.replace("\n", "<br>") + "</p>"));
        } catch (Exception ex) {
            // Un correo que no sale no puede deshacer un retracto ya ejercido: el derecho está
            // ejercido y la devolución, registrada. Se anota y se sigue.
            log.warn("No se pudo avisar a {} de «{}»: {}", user.getEmail(), asunto, ex.getMessage());
        }
    }

    private String formato(long cop) {
        return "$" + String.format("%,d", cop).replace(',', '.') + " COP";
    }
}
