package co.orion.billing.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import co.orion.billing.domain.Payout;
import co.orion.billing.domain.PayoutAdjustment;
import co.orion.billing.domain.PayoutCalculator;
import co.orion.billing.domain.PayoutCalculator.Candidate;
import co.orion.billing.domain.PayoutCalculator.Fortnight;
import co.orion.billing.domain.PayoutCut;
import co.orion.billing.domain.PayoutDestination;
import co.orion.billing.domain.PayoutLine;
import co.orion.billing.domain.PayoutLineKind;
import co.orion.billing.domain.PayoutPaidEvent;
import co.orion.billing.domain.PayoutStatus;
import co.orion.billing.persistence.PayoutAdjustmentRepository;
import co.orion.billing.persistence.PayoutCutRepository;
import co.orion.billing.persistence.PayoutLineRepository;
import co.orion.billing.persistence.PayoutRepository;
import co.orion.catalog.application.PlatformSettingsService;
import co.orion.identity.application.AdminAuditService;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.time.BusinessZone;

/**
 * Las liquidaciones quincenales bajo mandato (brief de liquidaciones, pasos 3 y 4). El sistema
 * calcula en cada corte; el admin aprueba, transfiere por Bre-B y registra el pago.
 *
 * <p>No hay dispersión automática y es deliberado: repartir fondos de terceros de forma automática en
 * Colombia levanta requisitos regulatorios que no se resuelven con código. Lo que sí garantiza el
 * sistema es que cada liquidación cuadre al peso, que una clase no se pague dos veces (el índice
 * único de {@code payout_lines}) y que una liquidación pagada no se toque más.
 */
@Service
public class PayoutService {

    private static final String CLAIM_WINDOW_HOURS = "dispute_report_window_hours";
    private static final Set<PayoutStatus> ABIERTAS = Set.of(PayoutStatus.DRAFT, PayoutStatus.ON_HOLD);

    private final PayoutRepository payouts;
    private final PayoutLineRepository lines;
    private final PayoutAdjustmentRepository adjustments;
    private final PayoutCutRepository cuts;
    private final PayoutCandidates candidates;
    private final PayoutHolds holds;
    private final PayoutDetailsService details;
    private final PlatformSettingsService settings;
    private final AdminAuditService audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate enSuTransaccion;
    private final Clock clock;

    public PayoutService(PayoutRepository payouts, PayoutLineRepository lines, PayoutAdjustmentRepository adjustments,
                         PayoutCutRepository cuts, PayoutCandidates candidates, PayoutHolds holds,
                         PayoutDetailsService details, PlatformSettingsService settings, AdminAuditService audit,
                         ApplicationEventPublisher events, PlatformTransactionManager transacciones, Clock clock) {
        this.payouts = payouts;
        this.lines = lines;
        this.adjustments = adjustments;
        this.cuts = cuts;
        this.candidates = candidates;
        this.holds = holds;
        this.details = details;
        this.settings = settings;
        this.audit = audit;
        this.events = events;
        this.enSuTransaccion = new TransactionTemplate(transacciones);
        this.clock = clock;
    }

    /* ---------------------------------------------------------------------------------------- */
    /* El corte                                                                                   */
    /* ---------------------------------------------------------------------------------------- */

    /**
     * El corte de una quincena: una liquidación por profe con algo que entregar. Idempotente: si la
     * quincena ya se cortó no hace nada, y si una corrida se cayó a mitad, la siguiente crea solo las
     * que faltan (cada profe va en su transacción, y el único de profe y quincena impide duplicar).
     *
     * @return cuántas liquidaciones creó (0 si la quincena ya estaba cortada)
     */
    public int runCut(Fortnight quincena) {
        if (cuts.existsById(quincena.start())) {
            return 0;
        }
        Duration reclamo = claimWindow();
        Map<UUID, List<Candidate>> porProfe = candidates.byProfessor();
        Set<UUID> profes = new java.util.LinkedHashSet<>(porProfe.keySet());
        profes.addAll(adjustments.findProfessorsWithPendingAdjustments());

        int creadas = 0;
        for (UUID profe : profes) {
            if (payouts.existsByProfessorIdAndPeriodStart(profe, quincena.start())) {
                continue;
            }
            List<Candidate> suyas = porProfe.getOrDefault(profe, List.of());
            Boolean creada = enSuTransaccion.execute(estado -> crear(profe, quincena, suyas, reclamo));
            if (Boolean.TRUE.equals(creada)) {
                creadas++;
            }
        }
        final int total = creadas;
        enSuTransaccion.executeWithoutResult(estado -> cuts.save(new PayoutCut(quincena, clock.instant(), total)));
        return creadas;
    }

    private boolean crear(UUID profe, Fortnight quincena, List<Candidate> suyas, Duration reclamo) {
        List<PayoutAdjustment> pendientes = adjustments.findByProfessorIdAndAppliedPayoutIdIsNullOrderByCreatedAtAsc(profe);
        PayoutCalculator.Result r = PayoutCalculator.calculate(suyas,
                pendientes.stream().map(PayoutAdjustment::pending).toList(), quincena.cutoff(), reclamo);
        if (r.lines().isEmpty()) {
            return false;
        }
        Payout payout = payouts.saveAndFlush(new Payout(profe, quincena, PayoutCalculator.committedPayDate(quincena)));
        guardar(payout, r, pendientes);
        return true;
    }

    /** Las líneas, los totales, los ajustes aplicados, y el estado que corresponde. */
    private void guardar(Payout payout, PayoutCalculator.Result r, List<PayoutAdjustment> pendientes) {
        lines.saveAll(r.lines().stream().map(l -> new PayoutLine(payout.getId(), l)).toList());
        for (PayoutAdjustment a : pendientes) {
            a.applyTo(payout.getId());
        }
        adjustments.saveAll(pendientes);
        payout.setTotals(r.grossCop(), r.commissionCop(), r.adjustmentsCop());
        if (r.netCop() <= 0) {
            // En cero o en negativo no se paga (regla 10): su saldo pasa a la siguiente como arrastre.
            payout.carryOver();
            if (r.netCop() < 0) {
                adjustments.save(new PayoutAdjustment(payout.getProfessorId(), null, PayoutLineKind.CARRY_OVER,
                        r.netCop(), "Saldo de la liquidación del " + payout.getPeriodStart() + " al " + payout.getPeriodEnd()));
            }
        } else {
            payout.applyHold(holds.motivo(payout.getProfessorId()).orElse(null));
        }
        payouts.save(payout);
    }

    /**
     * Rehace un borrador (o una retenida) con lo que hay hoy: si entretanto se cerró un reclamo, su
     * clase entra. Mismo corte, así que el plazo de reclamo se mide igual. Una aprobada o pagada no se
     * regenera nunca.
     */
    @Transactional
    public Optional<Payout> regenerate(UUID payoutId, UUID adminId) {
        Payout payout = get(payoutId);
        if (!payout.isEditable()) {
            throw new BusinessRuleViolationException("Solo un borrador o una liquidación retenida se puede regenerar");
        }
        lines.deleteByPayoutId(payout.getId());
        adjustments.releaseFrom(payout.getId());
        lines.flush();
        if (adminId != null) {
            audit.record(adminId, "REGENERATE_PAYOUT", "PAYOUT", payout.getId(), null);
        }

        List<PayoutAdjustment> pendientes = adjustments.findByProfessorIdAndAppliedPayoutIdIsNullOrderByCreatedAtAsc(
                payout.getProfessorId());
        PayoutCalculator.Result r = PayoutCalculator.calculate(candidates.ofProfessor(payout.getProfessorId()),
                pendientes.stream().map(PayoutAdjustment::pending).toList(), payout.getCutoffAt(), claimWindow());
        if (r.lines().isEmpty()) {
            payouts.delete(payout);
            return Optional.empty();
        }
        guardar(payout, r, pendientes);
        return Optional.of(payout);
    }

    /**
     * Retenida con su motivo, o de vuelta a borrador: se mira en cada lectura de la vista del admin y
     * antes de aprobar. Así, en cuanto el profe acepta el acuerdo o registra sus datos, su liquidación
     * aparece lista sin que nadie la toque.
     */
    @Transactional
    public void refreshHolds() {
        for (Payout p : payouts.findByStatusIn(ABIERTAS)) {
            if (p.getAmountCop() > 0) {
                p.applyHold(holds.motivo(p.getProfessorId()).orElse(null));
            }
        }
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Aprobar y pagar                                                                           */
    /* ---------------------------------------------------------------------------------------- */

    @Transactional
    public Payout approve(UUID payoutId, UUID adminId) {
        Payout payout = get(payoutId);
        if (payout.isEditable() && payout.getAmountCop() > 0) {
            payout.applyHold(holds.motivo(payout.getProfessorId()).orElse(null));
        }
        if (payout.getStatus() == PayoutStatus.ON_HOLD) {
            throw new BusinessRuleViolationException("Está retenida: " + payout.getHoldReason().toLowerCase());
        }
        if (payout.getStatus() != PayoutStatus.DRAFT) {
            throw new BusinessRuleViolationException("Solo un borrador se aprueba (esta está "
                    + payout.getStatus().etiqueta().toLowerCase() + ")");
        }
        payout.approve(adminId, clock.instant());
        audit.record(adminId, "APPROVE_PAYOUT", "PAYOUT", payout.getId(),
                "{\"netCop\":" + payout.getAmountCop() + "}");
        return payouts.save(payout);
    }

    /**
     * Registra la transferencia: la fecha, la referencia Bre-B y la confirmación de que el nombre que
     * mostró el banco coincide con el titular. Guarda la llave y el titular con los que se pagó, y le
     * avisa al profe (campana, push y correo con el comprobante).
     */
    @Transactional
    public Payout pay(UUID payoutId, LocalDate paidOn, String reference, boolean holderVerified, UUID adminId) {
        Payout payout = get(payoutId);
        if (payout.getStatus() != PayoutStatus.APPROVED) {
            throw new BusinessRuleViolationException("Solo una liquidación aprobada se paga (esta está "
                    + payout.getStatus().etiqueta().toLowerCase() + ")");
        }
        PayoutDestination destino = details.full(payout.getProfessorId())
                .orElseThrow(() -> new BusinessRuleViolationException("El profe no tiene datos de pago registrados"));
        if (paidOn == null || paidOn.isAfter(LocalDate.now(clock.withZone(BusinessZone.BOGOTA)))) {
            throw new BusinessRuleViolationException("La fecha de la transferencia no puede ser futura");
        }
        try {
            payout.markPaid(paidOn, reference, destino, holderVerified, adminId, clock.instant());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleViolationException(ex.getMessage());
        }
        Payout pagada = payouts.save(payout);
        audit.record(adminId, "PAY_PAYOUT", "PAYOUT", pagada.getId(),
                "{\"netCop\":" + pagada.getAmountCop() + ",\"reference\":\"" + pagada.getReference().replace("\"", "'") + "\"}");
        events.publishEvent(new PayoutPaidEvent(pagada.getId(), pagada.getProfessorId(), pagada.getAmountCop(),
                pagada.getPeriodStart(), pagada.getPeriodEnd()));
        return pagada;
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Ajustes por devolución                                                                    */
    /* ---------------------------------------------------------------------------------------- */

    /**
     * Una clase se devolvió al estudiante (reclamo resuelto a su favor, devolución). Si ya se le pagó
     * al profe, su neto se descuenta en la siguiente liquidación (regla 9): una pagada nunca se toca.
     * Si todavía estaba en un borrador, el borrador se rehace sin ella.
     *
     * <p>Con las reglas de hoy no ocurre (una clase liquidada ya no admite reclamo ni devolución),
     * pero queda listo por si esas reglas cambian.
     */
    @Transactional
    public void onRefunded(UUID bookingId) {
        lines.findFirstByBookingIdAndKindIn(bookingId, List.of(PayoutLineKind.CLASS, PayoutLineKind.LATE_CANCELLATION))
                .ifPresent(linea -> {
                    Payout payout = get(linea.getPayoutId());
                    if (payout.isEditable()) {
                        regenerate(payout.getId(), null);
                    } else if (!adjustments.existsByBookingIdAndKind(bookingId, PayoutLineKind.REFUND_ADJUSTMENT)) {
                        adjustments.save(new PayoutAdjustment(payout.getProfessorId(), bookingId,
                                PayoutLineKind.REFUND_ADJUSTMENT, -linea.getNetCop(),
                                "Devolución: " + linea.getDescription().toLowerCase()));
                    }
                });
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Lecturas                                                                                   */
    /* ---------------------------------------------------------------------------------------- */

    @Transactional(readOnly = true)
    public Payout get(UUID payoutId) {
        return payouts.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Liquidación no encontrada"));
    }

    @Transactional(readOnly = true)
    public List<PayoutLine> linesOf(UUID payoutId) {
        return lines.findByPayoutIdOrderByClassAtAscDescriptionAsc(payoutId);
    }

    @Transactional(readOnly = true)
    public List<Payout> ofFortnight(LocalDate periodStart) {
        return payouts.findByPeriodStartOrderByCreatedAtAsc(periodStart);
    }

    @Transactional(readOnly = true)
    public List<Payout> ofProfessor(UUID professorId) {
        return payouts.findByProfessorIdOrderByPeriodStartDesc(professorId);
    }

    @Transactional(readOnly = true)
    public List<PayoutCut> cuts() {
        return cuts.findAllByOrderByPeriodStartDesc();
    }

    @Transactional(readOnly = true)
    public List<Payout> all() {
        return payouts.findAllByOrderByCreatedAtDesc();
    }

    public Duration claimWindow() {
        return Duration.ofHours(settings.getInt(CLAIM_WINDOW_HOURS));
    }

    public Instant now() {
        return clock.instant();
    }
}
