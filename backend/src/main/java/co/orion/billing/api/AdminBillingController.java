package co.orion.billing.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.billing.application.CreditService;
import co.orion.billing.application.PaymentQueryService;
import co.orion.billing.application.PaymentLifecycleService;
import co.orion.billing.application.PaymentView;
import co.orion.billing.domain.CreditReason;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/**
 * Conciliación. Es una pantalla, no un SELECT a mano en producción: quien tiene que cuadrar la plata
 * no debería necesitar una consola de Postgres para hacerlo. Las liquidaciones viven en
 * {@link AdminPayoutsController}.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminBillingController {

    private final PaymentQueryService paymentQueries;
    private final CreditService credits;
    private final PaymentLifecycleService lifecycle;

    public AdminBillingController(PaymentQueryService paymentQueries,
                                  CreditService credits,
                                  PaymentLifecycleService lifecycle) {
        this.paymentQueries = paymentQueries;
        this.credits = credits;
        this.lifecycle = lifecycle;
    }

    @GetMapping("/payments")
    public List<AdminPaymentResponse> payments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID professorId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return paymentQueries.search(status, professorId, studentId, from, to).stream()
                .map(AdminBillingController::toResponse)
                .toList();
    }

    /**
     * Compensa a un estudiante a mano. Existe porque la conciliación marca casos que el sistema no
     * decide solo —un cobro que entró y una clase que no existe— y una pantalla que señala un
     * problema sin ofrecer cómo resolverlo no sirve de nada.
     *
     * Con {@code bookingId} el abono además CIERRA el pago: sin eso el incidente seguiría marcado y
     * se compensaría dos veces. Sin {@code bookingId} es un ajuste suelto, sin pago detrás.
     */
    @PostMapping("/credits")
    public CreditResponse grantCredit(@AuthenticationPrincipal OrionUserDetails principal,
                                      @Valid @RequestBody GrantCreditRequest body) {
        CreditReason reason;
        try {
            reason = CreditReason.valueOf(body.reason().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException("Motivo de crédito desconocido: " + body.reason());
        }

        if (body.bookingId() != null) {
            return CreditResponse.from(lifecycle.compensate(
                    body.bookingId(), body.amountCop(), reason, principal.user().getId()));
        }
        return CreditResponse.from(credits.grant(
                body.studentId(), body.amountCop(), reason, null, null, principal.user().getId()));
    }

    private static AdminPaymentResponse toResponse(PaymentView view) {
        return AdminPaymentResponse.of(
                view.payment(), view.booking(), view.studentName(), view.professorName());
    }
}
