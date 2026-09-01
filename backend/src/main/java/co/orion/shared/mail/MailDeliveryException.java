package co.orion.shared.mail;

/** Falla de entrega de un correo. Runtime: quien envía la traga y registra, nunca rompe el negocio. */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public MailDeliveryException(String message) {
        super(message);
    }
}
