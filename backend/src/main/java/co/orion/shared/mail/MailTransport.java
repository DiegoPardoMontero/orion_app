package co.orion.shared.mail;

/**
 * Cómo salen los correos. Dos implementaciones intercambiables por configuración
 * (`orion.mail.transport`): {@code SmtpMailTransport} para desarrollo (Mailpit) y
 * {@code ResendMailTransport} para producción (API HTTP sobre 443, porque las PaaS como Railway
 * bloquean los puertos SMTP salientes). Quien compone el correo no sabe por dónde viaja.
 */
public interface MailTransport {

    /** Envía el correo. Lanza una excepción si no se pudo entregar; nunca cuelga (timeouts). */
    void send(OutgoingEmail email);
}
