package co.orion.billing.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.billing.application.PayoutCertificates;
import co.orion.billing.application.PayoutViews;
import co.orion.shared.security.OrionUserDetails;

/**
 * Las liquidaciones del propio profe (brief de liquidaciones, paso 5): el próximo corte, lo que tiene
 * por liquidar con su motivo, su historial y el comprobante de cada una. Una liquidación de otro profe
 * responde 404, como si no existiera.
 */
@RestController
@RequestMapping("/api/v1/me/payouts")
public class MyPayoutsController {

    private final PayoutViews views;
    private final PayoutCertificates certificates;

    public MyPayoutsController(PayoutViews views, PayoutCertificates certificates) {
        this.views = views;
        this.certificates = certificates;
    }

    @GetMapping
    public PayoutViews.ForProfessor mine(@AuthenticationPrincipal OrionUserDetails principal) {
        return views.forProfessor(principal.user().getId());
    }

    /** Sus certificados anuales ya firmados y subidos. */
    @GetMapping("/certificates")
    public java.util.List<PayoutCertificates.Uploaded> certificates(@AuthenticationPrincipal OrionUserDetails principal) {
        return certificates.of(principal.user().getId());
    }

    /** El enlace (firmado, de pocos minutos) para descargar el de un año. */
    @GetMapping("/certificates/{year}/url")
    public java.util.Map<String, String> certificateUrl(@PathVariable int year,
                                                       @AuthenticationPrincipal OrionUserDetails principal) {
        return java.util.Map.of("url", certificates.signedUrl(principal.user().getId(), year));
    }

    @GetMapping("/{id}/receipt")
    public PayoutViews.Receipt receipt(@PathVariable UUID id, @AuthenticationPrincipal OrionUserDetails principal) {
        return views.receiptOf(id, principal.user().getId());
    }
}
