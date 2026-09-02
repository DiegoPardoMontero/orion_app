package co.orion.billing.api;

import java.util.UUID;

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
     * {@code transactionId} llega en la URL de vuelta de Wompi. Cuando viene, antes de responder se
     * le pregunta a la pasarela por esa transacción y se aplica lo que diga: es la red de seguridad
     * para el webhook que se pierde. El servicio comprueba que la transacción sea de este pago, así
     * que un id inventado no confirma nada.
     */
    @GetMapping("/{id}/payment")
    public PaymentStatusResponse paymentOf(@AuthenticationPrincipal OrionUserDetails principal,
                                           @PathVariable UUID id,
                                           @RequestParam(required = false) String transactionId) {
        PaymentView view = paymentQueries.statusOf(principal.user(), id);

        if (transactionId != null && !transactionId.isBlank() && view.payment().isPending()) {
            payments.syncFromProvider(view.payment(), transactionId.trim());
            view = paymentQueries.statusOf(principal.user(), id);
        }

        return PaymentStatusResponse.of(
                view.booking(), view.payment(), checkout.resumeUrlFor(view.payment()));
    }
}
