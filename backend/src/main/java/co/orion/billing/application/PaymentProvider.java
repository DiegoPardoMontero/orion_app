package co.orion.billing.application;

import java.util.Map;

import co.orion.billing.domain.Payment;

/**
 * La pasarela, vista desde el dominio. Mismo patrón —y misma razón— que {@code MeetingLinkProvider}:
 * cambiar de proveedor no debe tocar el libro contable.
 *
 * No hay {@code refund()}: Wompi no expone un endpoint público de reembolso (solo anulación de
 * tarjeta antes del cierre), así que una devolución se hace hoy desde su panel y se registra en
 * Orión como crédito al estudiante. Declarar aquí un método que solo puede lanzar
 * UnsupportedOperationException sería peor que no tenerlo: haría creer que la plata vuelve sola.
 */
public interface PaymentProvider {

    /** El nombre que se guarda en {@code payments.provider}. */
    String name();

    /**
     * El cobro más pequeño que la pasarela acepta. Lo necesita el consumo de créditos: si el
     * crédito deja un resto por debajo de este mínimo, la reserva sería impagable.
     */
    long minimumChargeCop();

    /** Prepara el cobro y devuelve a dónde mandar al estudiante. */
    PaymentIntent createIntent(Payment payment, String reference, String returnUrl);

    /**
     * Verifica la firma y traduce el evento. Lanza {@link InvalidWebhookSignatureException} si la
     * firma no cuadra — antes de tocar nada.
     */
    ProviderEvent parseWebhook(String rawBody, Map<String, String> headers);

    /** Consulta el estado real de una transacción. Es el respaldo cuando el webhook no llega. */
    ProviderTransaction fetchTransaction(String transactionId);
}
