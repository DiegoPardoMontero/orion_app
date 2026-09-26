package co.orion.billing.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.Payout;
import co.orion.billing.domain.PayoutCalculator;
import co.orion.billing.domain.PayoutCalculator.Fortnight;
import co.orion.billing.domain.PayoutDestination;
import co.orion.billing.domain.PayoutLine;
import co.orion.billing.domain.PayoutStatus;
import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.persistence.UserRepository;
import co.orion.shared.config.LegalIdentity;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * Lo que se lee de las liquidaciones fuera del flujo del admin (brief de liquidaciones, paso 5): lo que
 * ve el profe en «Mis ganancias» y el comprobante de cada liquidación, que es el mismo en la pantalla,
 * al imprimir y en el correo de pago.
 */
@Service
public class PayoutViews {

    /** El texto fijo de «Mis ganancias», tal cual lo pide el brief. */
    public static final String EXPLICACION =
            "Orión recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena, menos la comisión.";

    private final PayoutService payouts;
    private final PayoutCandidates candidates;
    private final PayoutDetailsService details;
    private final PlatformSettingsService settings;
    private final LegalIdentity legal;
    private final UserRepository users;

    public PayoutViews(PayoutService payouts, PayoutCandidates candidates, PayoutDetailsService details,
                       PlatformSettingsService settings, LegalIdentity legal, UserRepository users) {
        this.payouts = payouts;
        this.candidates = candidates;
        this.details = details;
        this.settings = settings;
        this.legal = legal;
        this.users = users;
    }

    /** «Mis ganancias» del profe: el próximo corte, lo que tiene por liquidar y su historial. */
    @Transactional(readOnly = true)
    public ForProfessor forProfessor(UUID professorId) {
        Instant ahora = payouts.now();
        Fortnight proxima = PayoutCalculator.next(ahora);
        List<Pending> pendientes = candidates.pendingOf(professorId, ahora).stream()
                .map(c -> new Pending(c.bookingId(), c.classStart(), c.studentLabel(), c.kind().name(), c.netCop(),
                        PayoutCalculator.whenItEnters(c, ahora, payouts.claimWindow())))
                .toList();
        List<History> historial = payouts.ofProfessor(professorId).stream()
                .map(p -> new History(p.getId(), p.getPeriodStart(), p.getPeriodEnd(), p.getStatus().name(),
                        p.getStatus().etiqueta(), p.getHoldReason(), p.getAmountCop(), p.getCommittedPayDate(),
                        p.getPaidOn()))
                .toList();
        return new ForProfessor(EXPLICACION, proxima.cutoff(), proxima.start(), proxima.end(),
                PayoutCalculator.committedPayDate(proxima), pendientes, historial);
    }

    /** El comprobante de una liquidación del propio profe. De otro profe, como si no existiera. */
    @Transactional(readOnly = true)
    public Receipt receiptOf(UUID payoutId, UUID professorId) {
        Payout p = payouts.get(payoutId);
        if (!p.getProfessorId().equals(professorId)) {
            throw new ResourceNotFoundException("Liquidación no encontrada");
        }
        return receipt(p);
    }

    /** El comprobante, para el admin o para el correo. */
    @Transactional(readOnly = true)
    public Receipt receipt(UUID payoutId) {
        return receipt(payouts.get(payoutId));
    }

    private Receipt receipt(Payout p) {
        var profe = users.findById(p.getProfessorId()).orElse(null);
        var datos = details.mine(p.getProfessorId());
        String llave;
        String titular;
        String tipo;
        if (p.getStatus() == PayoutStatus.PAID) {
            llave = PayoutDestination.enmascararLlave(p.getPayeeKeyType(), p.getPayeeKey());
            titular = p.getPayeeHolder();
            tipo = p.getPayeeKeyType().etiqueta();
        } else {
            llave = datos.map(PayoutDetailsService.Masked::maskedKey).orElse(null);
            titular = datos.map(PayoutDetailsService.Masked::holderName).orElse(null);
            tipo = datos.map(PayoutDetailsService.Masked::keyTypeLabel).orElse(null);
        }
        String documentoDelProfe = datos.map(d -> d.documentType().name() + " " + d.maskedDocument()).orElse(null);
        List<ReceiptLine> lineas = payouts.linesOf(p.getId()).stream().map(ReceiptLine::of).toList();
        return new Receipt(p.getId(), p.getStatus().name(), p.getStatus().etiqueta(), p.getPeriodStart(),
                p.getPeriodEnd(), p.getCutoffAt(), p.getCommittedPayDate(), mandatario(), documentoDelMandatario(),
                profe != null ? profe.getFullName() : null, documentoDelProfe, lineas, p.getGrossCop(),
                p.getCommissionCop(), p.getAdjustmentsCop(), p.getAmountCop(), p.getPaidOn(), p.getReference(), tipo,
                llave, titular);
    }

    /** Del ajuste; vacío, el responsable de los datos legales. */
    public String mandatario() {
        String ajuste = settings.getString("mandatary_name");
        return ajuste == null || ajuste.isBlank() ? legal.responsable() : ajuste;
    }

    public String documentoDelMandatario() {
        String ajuste = settings.getString("mandatary_document");
        return ajuste == null || ajuste.isBlank() ? legal.documento() : ajuste;
    }

    public record Pending(UUID bookingId, Instant classAt, String studentLabel, String kind, long netCop,
                          String reason) {
    }

    public record History(UUID id, LocalDate periodStart, LocalDate periodEnd, String status, String statusLabel,
                          String holdReason, long netCop, LocalDate committedPayDate, LocalDate paidOn) {
    }

    public record ForProfessor(String explanation, Instant nextCutoff, LocalDate nextPeriodStart,
                               LocalDate nextPeriodEnd, LocalDate nextPayDate, List<Pending> pending,
                               List<History> payouts) {
    }

    public record ReceiptLine(String kind, Instant classAt, String studentLabel, long grossCop,
                              Integer commissionRateBps, long commissionCop, long netCop, String description) {

        static ReceiptLine of(PayoutLine l) {
            return new ReceiptLine(l.getKind().name(), l.getClassAt(), l.getStudentLabel(), l.getGrossCop(),
                    l.getCommissionRateBps(), l.getCommissionCop(), l.getNetCop(), l.getDescription());
        }
    }

    public record Receipt(UUID id, String status, String statusLabel, LocalDate periodStart, LocalDate periodEnd,
                          Instant cutoffAt, LocalDate committedPayDate, String mandataryName,
                          String mandataryDocument, String professorName, String professorDocument,
                          List<ReceiptLine> lines, long grossCop, long commissionCop, long adjustmentsCop,
                          long netCop, LocalDate paidOn, String reference, String payeeKeyTypeLabel,
                          String payeeMaskedKey, String payeeHolder) {
    }
}
