package co.orion.billing.api;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.billing.application.CheckoutService;
import co.orion.billing.application.PaymentQueryService;
import co.orion.billing.application.PaymentView;
import co.orion.billing.application.PaymentWebhookService;
import co.orion.shared.security.OrionUserDetails;

/**
 * El estado del pago de una reserva. Lo consulta la pantalla de retorno de la pasarela en bucle:
 * PSE no responde al instante, y el estudiante necesita ver algo mientras tanto.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingPaymentController {

    private final PaymentQueryService paymentQueries;
    private final CheckoutService checkout;
    private final PaymentWebhookService payments;

    public BookingPaymentController(PaymentQueryService paymentQueries,
                                    CheckoutService checkout,
                                    PaymentWebhookService payments) {
        this.paymentQueries = paymentQueries;
        this.checkout = checkout;
        this.payments = payments;
    }

    /**
     * Forma de un id de Wompi ({@code 1234-1610641025-49201}). El id se pega a la ruta de una
     * petición saliente con nuestra llave: algo como {@code ../merchants/…} la llevaría a otro
     * recurso de su API. Lo que no tiene esta forma se ignora, como si no hubiera llegado.
     */
    private static final Pattern ID_DE_TRANSACCION = Pattern.compile("[A-Za-z0-9-]{1,64}");

    /**
     * {@code transactionId} llega en la URL de vuelta de Wompi. Cuando viene, antes de responder se
     * le pregunta a la pasarela por esa transacción y se aplica lo que diga: es la red de seguridad
     * para el webhook que se pierde. El servicio comprueba que la transacción sea de este pago, así
     * que un id inventado no confirma nada.
     *
     * Se pregunta también cuando el pago ya se dio por perdido: si el cupo venció mientras el banco
     * se decidía, el cobro pudo haberse hecho igual y queda marcado para revisión en vez de
     * perderse.
     */
    @GetMapping("/{id}/payment")
    public PaymentStatusResponse paymentOf(@AuthenticationPrincipal OrionUserDetails principal,
                                           @PathVariable UUID id,
                                           @RequestParam(required = false) String transactionId) {
        PaymentView view = paymentQueries.statusOf(principal.user(), id);

        if (transactionId != null && ID_DE_TRANSACCION.matcher(transactionId.trim()).matches()
                && view.payment().canStillLearnFromProvider()) {
            payments.syncFromProvider(view.payment(), transactionId.trim());
            view = paymentQueries.statusOf(principal.user(), id);
        }

        return PaymentStatusResponse.of(
                view.booking(), view.payment(), checkout.resumeUrlFor(view.payment()));
    }
}
