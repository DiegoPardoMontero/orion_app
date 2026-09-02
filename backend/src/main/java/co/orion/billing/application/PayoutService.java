package co.orion.billing.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.Payment;
import co.orion.billing.domain.Payout;
import co.orion.billing.domain.PayoutItem;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.billing.persistence.PayoutItemRepository;
import co.orion.billing.persistence.PayoutRepository;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.error.ResourceNotFoundException;

/**
 * La liquidación a profesores: el sistema calcula, una persona transfiere.
 *
 * No hay dispersión automática y es deliberado (§4.1 del brief): repartir fondos de terceros de
 * forma automática en Colombia levanta requisitos regulatorios que no se resuelven con código, y a
 * este volumen automatizarlo sería construir un banco para no usarlo. Lo que sí garantiza el
 * sistema es que el reporte cuadre al peso y que una clase no se pague dos veces —de eso responde
 * el UNIQUE de {@code payout_items}, no un {@code if}.
 */
@Service
public class PayoutService {

    private final PayoutRepository payouts;
    private final PayoutItemRepository items;
    private final PaymentRepository payments;
    private final Clock clock;

    public PayoutService(PayoutRepository payouts,
                         PayoutItemRepository items,
                         PaymentRepository payments,
                         Clock clock) {
        this.payouts = payouts;
        this.items = items;
        this.payments = payments;
        this.clock = clock;
    }

    /**
     * Una liquidación por profesor con algo que cobrar en el período. Solo entran pagos RELEASED
     * —clases que de verdad ocurrieron— y que no estén ya en otra liquidación.
     */
    @Transactional
    public List<Payout> generate(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null || periodStart.isAfter(periodEnd)) {
            throw new BusinessRuleViolationException("El período de liquidación no es válido");
        }
        Instant from = periodStart.atStartOfDay(BusinessZone.BOGOTA).toInstant();
        Instant to = periodEnd.plusDays(1).atStartOfDay(BusinessZone.BOGOTA).toInstant();

        List<Payout> generated = new ArrayList<>();
        for (UUID professorId : payments.findProfessorsWithPayableEarnings(from, to)) {
            List<Payment> payable = payments.findPayableOfProfessor(professorId, from, to);
            if (payable.isEmpty()) {
                continue;
            }
            long total = payable.stream().mapToLong(Payment::getProfessorEarningsCop).sum();
            Payout payout = payouts.saveAndFlush(
                    new Payout(professorId, periodStart, periodEnd, total));

            items.saveAll(payable.stream()
                    .map(payment -> new PayoutItem(payout.getId(), payment.getId()))
                    .toList());
            generated.add(payout);
        }
        return generated;
    }

    @Transactional
    public Payout markPaid(UUID payoutId, String reference) {
        Payout payout = payouts.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Liquidación no encontrada"));
        payout.markPaid(reference, clock.instant());
        return payouts.save(payout);
    }

    @Transactional(readOnly = true)
    public List<Payout> all() {
        return payouts.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Payout get(UUID payoutId) {
        return payouts.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Liquidación no encontrada"));
    }

    @Transactional(readOnly = true)
    public List<Payment> paymentsOf(UUID payoutId) {
        List<UUID> paymentIds = items.findByIdPayoutId(payoutId).stream()
                .map(PayoutItem::getPaymentId)
                .toList();
        return payments.findAllById(paymentIds);
    }
}
