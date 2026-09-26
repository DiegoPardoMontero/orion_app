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

import co.orion.billing.application.PayoutDetailsService;
import co.orion.billing.application.PayoutService;
import co.orion.billing.application.PayoutViews;
import co.orion.billing.domain.Payout;
import co.orion.billing.domain.PayoutCalculator;
import co.orion.billing.domain.PayoutCalculator.Fortnight;
import co.orion.billing.domain.PayoutCut;
import co.orion.billing.domain.PayoutDestination;
import co.orion.billing.domain.PayoutLine;
import co.orion.billing.domain.PayoutStatus;
import co.orion.identity.application.AdminAuditService;
import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;
import co.orion.shared.time.BusinessZone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Las liquidaciones del admin (brief de liquidaciones, paso 4): una vista por quincena, el detalle
 * con sus líneas, aprobar, registrar el pago y regenerar un borrador. La llave Bre-B completa solo se
 * ve en el flujo de pago de una liquidación aprobada, y cada vez que se ve queda auditado.
 */
@RestController
@RequestMapping("/api/v1/admin/payouts")
public class AdminPayoutsController {

    private static final List<PayoutStatus> POR_TRANSFERIR = List.of(PayoutStatus.DRAFT, PayoutStatus.APPROVED);

    private final PayoutService payouts;
    private final PayoutDetailsService details;
    private final UserRepository users;
    private final AdminAuditService audit;
    private final PayoutViews views;

    public AdminPayoutsController(PayoutService payouts, PayoutDetailsService details, UserRepository users,
                                  AdminAuditService audit, PayoutViews views) {
        this.payouts = payouts;
        this.details = details;
        this.users = users;
        this.audit = audit;
        this.views = views;
    }

    /** Las quincenas cortadas, de la más reciente a la más vieja, y cuándo es el próximo corte. */
    @GetMapping("/fortnights")
    public FortnightsResponse fortnights() {
        Fortnight proxima = PayoutCalculator.next(payouts.now());
        return new FortnightsResponse(
                new Upcoming(proxima.start(), proxima.end(), proxima.cutoff(), PayoutCalculator.committedPayDate(proxima)),
                payouts.cuts().stream().map(c -> new CutView(c.getPeriodStart(), c.getPeriodEnd(), c.getCutoffAt(),
                        PayoutCalculator.committedPayDate(PayoutCalculator.closedBy(
                                c.getCutoffAt().atZone(BusinessZone.BOGOTA).toLocalDate())),
                        c.getRanAt(), c.getPayoutsCreated())).toList());
    }

    /**
     * Una quincena: cada profe con su estado, su neto y la fecha comprometida; el total a transferir y
     * las retenidas con su motivo. Sin {@code periodStart}, la del último corte.
     */
    @GetMapping
    public FortnightResponse fortnight(@RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart) {
        payouts.refreshHolds();
        List<PayoutCut> cortes = payouts.cuts();
        LocalDate inicio = periodStart != null ? periodStart
                : cortes.isEmpty() ? null : cortes.getFirst().getPeriodStart();
        if (inicio == null) {
            return new FortnightResponse(null, null, null, null, null, 0, 0, 0, List.of());
        }
        PayoutCut corte = cortes.stream().filter(c -> c.getPeriodStart().equals(inicio)).findFirst().orElse(null);
        List<Payout> deLaQuincena = payouts.ofFortnight(inicio);
        List<PayoutRow> filas = rows(deLaQuincena);
        long porTransferir = deLaQuincena.stream().filter(p -> POR_TRANSFERIR.contains(p.getStatus()))
                .mapToLong(Payout::getAmountCop).sum();
        long pagado = deLaQuincena.stream().filter(p -> p.getStatus() == PayoutStatus.PAID)
                .mapToLong(Payout::getAmountCop).sum();
        int retenidas = (int) deLaQuincena.stream().filter(p -> p.getStatus() == PayoutStatus.ON_HOLD).count();
        Fortnight q = corte != null
                ? PayoutCalculator.closedBy(corte.getCutoffAt().atZone(BusinessZone.BOGOTA).toLocalDate())
                : null;
        return new FortnightResponse(inicio, q != null ? q.end() : null, q != null ? q.cutoff() : null,
                q != null ? PayoutCalculator.committedPayDate(q) : null, corte != null ? corte.getRanAt() : null,
                porTransferir, pagado, retenidas, filas);
    }

    @GetMapping("/{id}")
    public PayoutDetailResponse detail(@PathVariable UUID id) {
        return detalle(payouts.get(id));
    }

    /**
     * La llave Bre-B completa, para transferir. Solo de una liquidación aprobada (es el único momento
     * en que hace falta), y queda en la auditoría quién la vio y cuándo.
     */
    @GetMapping("/{id}/payee")
    public PayeeResponse payee(@PathVariable UUID id, @AuthenticationPrincipal OrionUserDetails principal) {
        Payout payout = payouts.get(id);
        if (payout.getStatus() != PayoutStatus.APPROVED) {
            throw new BusinessRuleViolationException("La llave completa solo se ve para pagar una liquidación aprobada");
        }
        PayoutDestination d = details.full(payout.getProfessorId())
                .orElseThrow(() -> new BusinessRuleViolationException("El profe no tiene datos de pago registrados"));
        audit.record(principal.user().getId(), "VIEW_PAYOUT_PAYEE", "PAYOUT", payout.getId(), null);
        return new PayeeResponse(d.keyType().name(), d.keyType().etiqueta(), d.keyValue(), d.documentType().name(),
                d.documentNumber(), d.holderName());
    }

    /** El comprobante, el mismo que ve el profe y que le llega por correo al pagar. */
    @GetMapping("/{id}/receipt")
    public PayoutViews.Receipt receipt(@PathVariable UUID id) {
        return views.receipt(id);
    }

    @PostMapping("/{id}/approve")
    public PayoutDetailResponse approve(@PathVariable UUID id, @AuthenticationPrincipal OrionUserDetails principal) {
        return detalle(payouts.approve(id, principal.user().getId()));
    }

    @PostMapping("/{id}/pay")
    public PayoutDetailResponse pay(@PathVariable UUID id, @Valid @RequestBody PayRequest body,
                                    @AuthenticationPrincipal OrionUserDetails principal) {
        return detalle(payouts.pay(id, body.paidOn(), body.reference(), body.holderVerified(), principal.user().getId()));
    }

    /** Rehace un borrador con lo de hoy (p. ej., un reclamo que se cerró). Si ya no queda nada, desaparece. */
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<PayoutDetailResponse> regenerate(@PathVariable UUID id,
                                                           @AuthenticationPrincipal OrionUserDetails principal) {
        return payouts.regenerate(id, principal.user().getId())
                .map(p -> ResponseEntity.ok(detalle(p)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * El CSV de una liquidación: una fila por línea y una de total, para que la suma se pueda verificar
     * sin abrir la aplicación. En UTF-8 con BOM, para que Excel lea bien las tildes.
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        Payout payout = payouts.get(id);
        StringBuilder csv = new StringBuilder("﻿fecha_clase,estudiante,concepto,bruto_cop,comision_pct,comision_cop,neto_cop\n");
        for (PayoutLine l : payouts.linesOf(id)) {
            csv.append(l.getClassAt() != null ? fechaBogota(l.getClassAt()) : "").append(',')
                    .append(quote(l.getStudentLabel())).append(',')
                    .append(quote(l.getDescription())).append(',')
                    .append(l.getGrossCop()).append(',')
                    .append(l.getCommissionRateBps() != null ? porcentaje(l.getCommissionRateBps()) : "").append(',')
                    .append(l.getCommissionCop()).append(',')
                    .append(l.getNetCop()).append('\n');
        }
        csv.append("TOTAL,,,").append(payout.getGrossCop()).append(",,").append(payout.getCommissionCop()).append(',')
                .append(payout.getAmountCop()).append('\n');
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"liquidacion-" + payout.getPeriodStart() + "-" + payout.getId() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private PayoutDetailResponse detalle(Payout p) {
        PayoutRow fila = rows(List.of(p)).getFirst();
        // Pagada: con la llave con que se pagó. Si no, con los datos que tiene hoy el profe, enmascarados.
        String llave;
        String titular;
        String tipo;
        boolean registrados;
        if (p.getStatus() == PayoutStatus.PAID) {
            llave = PayoutDestination.enmascararLlave(p.getPayeeKeyType(), p.getPayeeKey());
            titular = p.getPayeeHolder();
            tipo = p.getPayeeKeyType().etiqueta();
            registrados = true;
        } else {
            var actuales = details.mine(p.getProfessorId());
            registrados = actuales.isPresent();
            llave = actuales.map(PayoutDetailsService.Masked::maskedKey).orElse(null);
            titular = actuales.map(PayoutDetailsService.Masked::holderName).orElse(null);
            tipo = actuales.map(PayoutDetailsService.Masked::keyTypeLabel).orElse(null);
        }
        return new PayoutDetailResponse(fila, p.getCutoffAt(), p.getApprovedAt(), p.getPaidAt(), registrados, tipo,
                llave, titular, payouts.linesOf(p.getId()).stream().map(LineView::of).toList());
    }

    private List<PayoutRow> rows(List<Payout> lista) {
        Map<UUID, User> gente = users.findAllById(lista.stream().map(Payout::getProfessorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return lista.stream().map(p -> new PayoutRow(p.getId(), p.getProfessorId(),
                gente.containsKey(p.getProfessorId()) ? gente.get(p.getProfessorId()).getFullName() : null,
                p.getPeriodStart(), p.getPeriodEnd(), p.getStatus().name(), p.getStatus().etiqueta(), p.getHoldReason(),
                p.getGrossCop(), p.getCommissionCop(), p.getAdjustmentsCop(), p.getAmountCop(), p.getCommittedPayDate(),
                p.getPaidOn(), p.getReference())).toList();
    }

    private static String fechaBogota(Instant instant) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(BusinessZone.BOGOTA).format(instant);
    }

    private static String porcentaje(int bps) {
        return bps % 100 == 0 ? String.valueOf(bps / 100) : String.valueOf(bps / 100.0);
    }

    /** Un nombre con coma partiría la columna del CSV en dos. */
    private static String quote(String value) {
        return value == null ? "" : '"' + value.replace("\"", "\"\"") + '"';
    }

    public record Upcoming(LocalDate start, LocalDate end, Instant cutoff, LocalDate committedPayDate) {
    }

    public record CutView(LocalDate periodStart, LocalDate periodEnd, Instant cutoffAt, LocalDate committedPayDate,
                          Instant ranAt, int payoutsCreated) {
    }

    public record FortnightsResponse(Upcoming next, List<CutView> cuts) {
    }

    public record FortnightResponse(LocalDate periodStart, LocalDate periodEnd, Instant cutoffAt,
                                    LocalDate committedPayDate, Instant ranAt, long toTransferCop, long paidCop,
                                    int onHold, List<PayoutRow> payouts) {
    }

    public record PayoutRow(UUID id, UUID professorId, String professorName, LocalDate periodStart,
                            LocalDate periodEnd, String status, String statusLabel, String holdReason, long grossCop,
                            long commissionCop, long adjustmentsCop, long netCop, LocalDate committedPayDate,
                            LocalDate paidOn, String reference) {
    }

    public record LineView(String kind, Instant classAt, String studentLabel, long grossCop,
                           Integer commissionRateBps, long commissionCop, long netCop, String description) {

        static LineView of(PayoutLine l) {
            return new LineView(l.getKind().name(), l.getClassAt(), l.getStudentLabel(), l.getGrossCop(),
                    l.getCommissionRateBps(), l.getCommissionCop(), l.getNetCop(), l.getDescription());
        }
    }

    public record PayoutDetailResponse(PayoutRow payout, Instant cutoffAt, Instant approvedAt, Instant paidAt,
                                       boolean payeeRegistered, String payeeKeyTypeLabel, String payeeMaskedKey,
                                       String payeeHolder, List<LineView> lines) {
    }

    public record PayeeResponse(String keyType, String keyTypeLabel, String key, String documentType,
                                String documentNumber, String holderName) {
    }

    public record PayRequest(@NotNull LocalDate paidOn, @NotBlank @Size(max = 140) String reference,
                             boolean holderVerified) {
    }
}
