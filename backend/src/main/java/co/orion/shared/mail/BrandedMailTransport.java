package co.orion.shared.mail;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Aplica la plantilla de marca a todo correo que sale, y delega el envío al transporte real.
 *
 * <p>Es un decorador y no una llamada más en cada redactor a propósito: hay cinco sitios que envían
 * correo (reservas, mensajes, recuperación, invitaciones y postulaciones) y el sexto que se escriba
 * saldría sin logo si el envoltorio dependiera de acordarse. Aquí no hay nada que recordar.
 *
 * <p>El wiring: este bean es {@code @Primary}, así que los redactores siguen inyectando
 * {@code MailTransport} sin cambiar una línea; los transportes concretos llevan el cualificador
 * {@code entrega} y solo este decorador los pide por ese nombre. Sin el cualificador habría que
 * confiar en que Spring desempate una autorreferencia, que funciona pero no se lee.
 */
@Component
@Primary
public class BrandedMailTransport implements MailTransport {

    private final MailTransport entrega;
    private final EmailLayout layout;

    public BrandedMailTransport(@Qualifier("entrega") MailTransport entrega, EmailLayout layout) {
        this.entrega = entrega;
        this.layout = layout;
    }

    @Override
    public void send(OutgoingEmail email) {
        entrega.send(conMarca(email));
    }

    /**
     * Solo se toca el HTML. La alternativa en texto plano se deja intacta: ahí no hay logo que
     * poner y meterle el armazón solo la ensuciaría para quien lee el correo sin formato.
     */
    private OutgoingEmail conMarca(OutgoingEmail email) {
        String html = email.htmlBody() == null ? null : layout.wrap(email.htmlBody());
        return new OutgoingEmail(
                email.to(),
                email.subject(),
                email.textBody(),
                html,
                email.attachmentFilename(),
                email.attachmentContent(),
                email.attachmentContentType());
    }
}
