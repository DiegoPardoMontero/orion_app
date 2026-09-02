package co.orion.billing.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.billing.application.CreditService;
import co.orion.billing.application.EarningsService;
import co.orion.billing.application.PaymentQueryService;
import co.orion.shared.security.OrionUserDetails;

/** Lo que cada quien ve de su propio dinero: el estudiante su saldo e historial, el profesor sus ganancias. */
@RestController
@RequestMapping("/api/v1/me")
public class MyBillingController {

    private final CreditService credits;
    private final PaymentQueryService paymentQueries;
    private final EarningsService earnings;
    private final Clock clock;

    public MyBillingController(CreditService credits,
                               PaymentQueryService paymentQueries,
                               EarningsService earnings,
                               Clock clock) {
        this.credits = credits;
        this.paymentQueries = paymentQueries;
        this.earnings = earnings;
        this.clock = clock;
    }

    @GetMapping("/credits")
    public CreditBalanceResponse myCredits(@AuthenticationPrincipal OrionUserDetails principal) {
        var usable = credits.usableCredits(principal.user().getId(), clock.instant());
        long balance = usable.stream().mapToLong(c -> c.getRemainingCop()).sum();
        return new CreditBalanceResponse(balance, usable.stream().map(CreditResponse::from).toList());
    }

    @GetMapping("/payments")
    public List<MyPaymentResponse> myPayments(@AuthenticationPrincipal OrionUserDetails principal) {
        return paymentQueries.ofStudent(principal.user().getId()).stream()
                .map(view -> MyPaymentResponse.of(view.payment(), view.booking(), view.professorName()))
                .toList();
    }

    @GetMapping("/earnings")
    public EarningsResponse myEarnings(
            @AuthenticationPrincipal OrionUserDetails principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return EarningsResponse.from(earnings.of(principal.user().getId(), from, to));
    }
}
