package co.orion.billing.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.billing.application.CreditService;
import co.orion.billing.application.PaymentQueryService;
import co.orion.billing.application.PaymentView;
import co.orion.billing.application.PayoutService;
import co.orion.billing.domain.CreditReason;
import co.orion.billing.domain.Payout;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/**
 * Conciliación y liquidación. La conciliación es una pantalla, no un SELECT a mano en producción:
 * quien tiene que cuadrar la plata no debería necesitar una consola de Postgres para hacerlo.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminBillingController {

    private final PaymentQueryService paymentQueries;
    private final PayoutService payouts;
    private final CreditService credits;
    private final UserRepository users;

    public AdminBillingController(PaymentQueryService paymentQueries,
                                  PayoutService payouts,
                                  CreditService credits,
                                  UserRepository users) {
        this.paymentQueries = paymentQueries;
        this.payouts = payouts;
        this.credits = credits;
        this.users = users;
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

    @GetMapping("/payouts")
    public List<PayoutResponse> listPayouts() {
        return decorate(payouts.all());
    }

    @PostMapping("/payouts/generate")
    public List<PayoutResponse> generate(@Valid @RequestBody GeneratePayoutsRequest body) {
        return decorate(payouts.generate(body.periodStart(), body.periodEnd()));
    }

    @PostMapping("/payouts/{id}/mark-paid")
    public PayoutResponse markPaid(@PathVariable UUID id,
                                   @Valid @RequestBody MarkPayoutPaidRequest body) {
        Payout payout = payouts.markPaid(id, body.reference());
        return PayoutResponse.of(payout, nameOf(payout.getProfessorId()));
    }

    /**
     * El CSV que se le pasa al banco o a la contadora. Una fila por clase y una de total, para que
     * la suma se pueda verificar sin abrir la aplicación.
     */
    @GetMapping("/payouts/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        Payout payout = payouts.get(id);
        List<PaymentView> lines = paymentQueries.decorate(payouts.paymentsOf(id));

        StringBuilder csv = new StringBuilder(
                "fecha_clase,estudiante,precio_cop,comision_cop,ganancia_cop\n");
        lines.forEach(view -> csv
                .append(view.booking() != null ? fechaBogota(view.booking().getStartsAt()) : "")
                .append(',')
                .append(quote(view.studentName())).append(',')
                .append(view.payment().getAmountCop()).append(',')
                .append(view.payment().getCommissionCop()).append(',')
                .append(view.payment().getProfessorEarningsCop()).append('\n'));
        csv.append("TOTAL,,,,").append(payout.getAmountCop()).append('\n');

        String filename = "liquidacion-" + payout.getId() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Compensa a un estudiante a mano. Existe porque la conciliación marca casos que el sistema no
     * decide solo —una clase cancelada por el estudiante cuyo pago ya había entrado— y una pantalla
     * que señala un problema sin ofrecer la forma de resolverlo no sirve de nada.
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
        return CreditResponse.from(credits.grant(
                body.studentId(), body.amountCop(), reason, body.bookingId(), null,
                principal.user().getId()));
    }

    private List<PayoutResponse> decorate(List<Payout> found) {
        Map<UUID, User> people = users
                .findAllById(found.stream().map(Payout::getProfessorId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return found.stream()
                .map(payout -> PayoutResponse.of(payout,
                        people.containsKey(payout.getProfessorId())
                                ? people.get(payout.getProfessorId()).getFullName() : null))
                .toList();
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(User::getFullName).orElse(null);
    }

    private static AdminPaymentResponse toResponse(PaymentView view) {
        return AdminPaymentResponse.of(
                view.payment(), view.booking(), view.studentName(), view.professorName());
    }

    /**
     * La fecha de la clase en hora de Bogotá y con segundos truncados. El CSV lo lee una persona —la
     * contadora, el banco—, no una máquina: un instante UTC crudo la obligaría a hacer la resta.
     */
    private static String fechaBogota(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(BusinessZone.BOGOTA)
                .format(instant);
    }

    /** Un nombre con coma partiría la columna del CSV en dos. */
    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
