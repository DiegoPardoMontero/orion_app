package co.orion.billing.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.CreditReason;
import co.orion.billing.domain.PaymentCreditApplication;
import co.orion.billing.domain.StudentCredit;
import co.orion.billing.persistence.PaymentCreditApplicationRepository;
import co.orion.billing.persistence.StudentCreditRepository;

/**
 * El saldo a favor del estudiante: cómo se gasta y cómo se devuelve.
 *
 * El crédito es un pasivo de Orión, no un descuento al profesor: cuando se gasta, el profesor
 * cobra su tarifa completa y la comisión sigue calculándose sobre el precio de la clase. Quien
 * pone la diferencia es Orión.
 */
@Service
public class CreditService {

    /** Cuánto crédito se gastó en total y de qué filas exactamente. */
    public record Applied(long totalCop, List<Consumption> consumptions) {

        public static Applied none() {
            return new Applied(0, List.of());
        }
    }

    public record Consumption(UUID creditId, long amountCop) {
    }

    private final StudentCreditRepository credits;
    private final PaymentCreditApplicationRepository applications;

    public CreditService(StudentCreditRepository credits,
                         PaymentCreditApplicationRepository applications) {
        this.credits = credits;
        this.applications = applications;
    }

    /**
     * Gasta crédito contra el precio de una clase, en orden FIFO por vencimiento. El bloqueo de
     * fila lo pone la consulta: sin él, dos pestañas leen el mismo saldo y las dos lo gastan.
     *
     * Hay un ajuste sutil al final: si tras aplicar el crédito quedara un resto por cobrar menor
     * que el mínimo de la pasarela, se aplica MENOS crédito para que el resto llegue justo a ese
     * mínimo. La alternativa —dejar un cobro de 400 pesos que ninguna pasarela acepta— sería una
     * reserva imposible de pagar. El estudiante conserva la diferencia como saldo.
     */
    @Transactional
    public Applied applyTo(UUID studentId, long priceCop, long minimumChargeCop, Instant now) {
        List<StudentCredit> usable = credits.findUsableForUpdate(studentId, now);
        if (usable.isEmpty() || priceCop <= 0) {
            return Applied.none();
        }

        long available = usable.stream().mapToLong(StudentCredit::getRemainingCop).sum();
        long toApply = Math.min(available, priceCop);
        long remainder = priceCop - toApply;
        if (remainder > 0 && remainder < minimumChargeCop) {
            toApply = priceCop - minimumChargeCop;
        }
        if (toApply <= 0) {
            return Applied.none();
        }

        List<Consumption> consumptions = new ArrayList<>();
        long pending = toApply;
        for (StudentCredit credit : usable) {
            if (pending == 0) {
                break;
            }
            long taken = credit.consumeUpTo(pending);
            if (taken > 0) {
                consumptions.add(new Consumption(credit.getId(), taken));
                pending -= taken;
            }
        }
        credits.saveAll(usable);
        return new Applied(toApply - pending, consumptions);
    }

    /** Deja constancia de qué crédito pagó qué parte del pago, para poder deshacerlo después. */
    @Transactional
    public void recordApplications(UUID paymentId, Applied applied) {
        applications.saveAll(applied.consumptions().stream()
                .map(c -> new PaymentCreditApplication(paymentId, c.creditId(), c.amountCop()))
                .toList());
    }

    /**
     * Devuelve a cada crédito exactamente lo que este pago le había quitado. Se usa cuando la
     * reserva vence o se cancela sin llegar a pagarse: el saldo vuelve con su motivo y su
     * vencimiento originales, que es justo lo que un crédito nuevo no podría reproducir.
     */
    @Transactional
    public void restore(UUID paymentId) {
        List<PaymentCreditApplication> applied = applications.findByPaymentId(paymentId);
        if (applied.isEmpty()) {
            return;
        }
        Map<UUID, StudentCredit> byId = credits
                .findAllById(applied.stream().map(PaymentCreditApplication::getCreditId).toList())
                .stream()
                .collect(Collectors.toMap(StudentCredit::getId, Function.identity()));

        applied.forEach(application -> {
            StudentCredit credit = byId.get(application.getCreditId());
            if (credit != null) {
                credit.restore(application.getAmountCop());
            }
        });
        credits.saveAll(byId.values());
        // El registro de aplicación se borra: el pago ya no consume nada y dejarlo haría que una
        // segunda devolución duplicara el saldo.
        applications.deleteAll(applied);
    }

    @Transactional
    public StudentCredit grant(UUID studentId,
                               long amountCop,
                               CreditReason reason,
                               UUID bookingId,
                               Instant expiresAt,
                               UUID createdBy) {
        return credits.save(new StudentCredit(studentId, amountCop, reason, bookingId, expiresAt, createdBy));
    }

    @Transactional(readOnly = true)
    public List<StudentCredit> usableCredits(UUID studentId, Instant now) {
        return credits.findUsable(studentId, now);
    }
}
