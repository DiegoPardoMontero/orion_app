package co.orion.billing.application;

/**
 * A dónde mandar al estudiante para que pague, y con qué referencia lo vamos a reconocer cuando
 * la pasarela nos devuelva la noticia.
 */
public record PaymentIntent(String provider,
                            String reference,
                            String checkoutUrl,
                            long amountInCents) {
}
