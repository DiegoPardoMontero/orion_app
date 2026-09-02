package co.orion.billing.application;

/**
 * La firma del webhook no cuadra. Se traduce a 401 y la base no se toca: un webhook sin verificar
 * es un endpoint público que confirma reservas gratis.
 */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
