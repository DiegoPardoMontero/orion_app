package co.orion.billing.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.orion.billing.domain.Payment;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.catalog.application.PlatformSettingsService;
import co.orion.catalog.domain.RateBreakdown;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.application.PaymentInitiator;
import co.orion.scheduling.application.PaymentTicket;
import co.orion.scheduling.domain.Booking;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.error.UnprocessableException;

/**
 * Abre el libro contable de una reserva: fija el precio y la comisión del momento, gasta el crédito
 * del estudiante y prepara el cobro de lo que quede.
 *
 * Es la implementación del puerto {@code PaymentInitiator} que declara scheduling — la misma
 * inversión que {@code MeetingLinkProvider}: scheduling pide "cóbrale esto" sin saber qué es una
 * comisión ni cómo se llama la pasarela.
 */
@Service
public class CheckoutService implements PaymentInitiator {

    private static final String COMMISSION_SETTING = "commission_rate_bps";
    private static final String HOLD_SETTING = "payment_hold_minutes";

    private final PaymentRepository payments;
    private final CreditService credits;
    private final PaymentProvider provider;
    private final ProfessorProfileRepository profiles;
    private final PlatformSettingsService settings;
    private final String appBaseUrl;
    private final Clock clock;

    public CheckoutService(PaymentRepository payments,
                           CreditService credits,
                           PaymentProvider provider,
                           ProfessorProfileRepository profiles,
                           PlatformSettingsService settings,
                           @Value("${orion.app.base-url}") String appBaseUrl,
                           Clock clock) {
        this.payments = payments;
        this.credits = credits;
        this.provider = provider;
        this.profiles = profiles;
        this.settings = settings;
        this.appBaseUrl = appBaseUrl;
        this.clock = clock;
    }

    @Override
    public Instant holdExpiry(Instant now) {
        return now.plus(Duration.ofMinutes(settings.getInt(HOLD_SETTING)));
    }

    @Override
    @Transactional
    public PaymentTicket initiate(Booking booking) {
        long priceCop = priceOf(booking);
        int commissionRateBps = settings.getInt(COMMISSION_SETTING);

        CreditService.Applied applied = credits.applyTo(
                booking.getStudentId(), priceCop, provider.minimumChargeCop(), clock.instant());

        // La comisión se calcula sobre el PRECIO de la clase, nunca sobre lo cobrado: el crédito
        // es un pasivo de Orión y no puede salir del bolsillo del profesor.
        RateBreakdown breakdown = RateBreakdown.of(priceCop, commissionRateBps);

        Payment payment = payments.saveAndFlush(new Payment(
                booking.getId(),
                booking.getStudentId(),
                booking.getProfessorId(),
                priceCop,
                applied.totalCop(),
                commissionRateBps,
                breakdown.commissionCop()));

        credits.recordApplications(payment.getId(), applied);

        if (payment.nothingToCharge()) {
            // Sin pasarela de por medio: el crédito ya pagó la clase entera.
            payment.markPaid(null, null, clock.instant());
            payments.save(payment);
            return ticket(payment, null);
        }

        String reference = referenceFor(payment);
        PaymentIntent intent = provider.createIntent(payment, reference, returnUrlFor(booking));
        payment.attachProviderReference(intent.provider(), intent.reference());
        payments.save(payment);

        return ticket(payment, intent.checkoutUrl());
    }

    /**
     * Vuelve a dar la URL de pago de una reserva que todavía nadie pagó. El estudiante cierra la
     * pestaña de PSE más a menudo de lo que parece, y sin esto su única salida sería dejar vencer
     * el cupo y reservarlo otra vez.
     *
     * Se puede reconstruir sin guardar nada porque la referencia es fija por pago y el importe no
     * cambia: la misma entrada produce la misma firma.
     */
    @Transactional(readOnly = true)
    public String resumeUrlFor(Payment payment) {
        if (!payment.isPending() || payment.nothingToCharge()) {
            return null;
        }
        return provider.createIntent(payment, payment.getProviderReference(),
                appBaseUrl + "/pago/" + payment.getBookingId()).checkoutUrl();
    }

    /**
     * La referencia que viaja a la pasarela y vuelve en el webhook. Es el id del pago, sin guiones
     * y con prefijo: fija por pago, así reintentar el checkout no crea una referencia huérfana y la
     * conciliación es una búsqueda exacta.
     */
    public static String referenceFor(Payment payment) {
        return "ORION-" + payment.getId().toString().replace("-", "");
    }

    private PaymentTicket ticket(Payment payment, String checkoutUrl) {
        return new PaymentTicket(
                payment.getId(),
                payment.getAmountCop(),
                payment.getCreditAppliedCop(),
                payment.getChargedCop(),
                checkoutUrl);
    }

    /**
     * El precio es la tarifa del profesor EN ESTE MOMENTO, copiada al pago. Si mañana la sube, la
     * reserva de hoy conserva la de hoy.
     */
    private long priceOf(Booking booking) {
        ProfessorProfile profile = profiles.findById(booking.getProfessorId())
                .orElseThrow(() -> new ResourceNotFoundException("Profesor no encontrado"));
        Long rate = profile.getHourlyRateCop();
        if (rate == null) {
            throw new UnprocessableException("El profesor todavía no tiene tarifa publicada");
        }
        return rate;
    }

    private String returnUrlFor(Booking booking) {
        return appBaseUrl + "/pago/" + booking.getId();
    }

}
