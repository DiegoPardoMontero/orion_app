package co.orion.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.application.PaymentIntent;
import co.orion.billing.application.PaymentProvider;
import co.orion.billing.application.ProviderEvent;
import co.orion.billing.application.ProviderTransaction;
import co.orion.billing.application.ProviderTransactionStatus;
import co.orion.billing.domain.Payment;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.api.BookingResponse;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * La red de seguridad para el webhook que no llega: la pantalla de retorno trae el id de
 * transacción y el backend le pregunta a la pasarela.
 *
 * Ese id viene del navegador, así que el test que de verdad importa es el segundo: una transacción
 * aprobada que NO es de este pago no puede confirmar la clase. Si eso fallara, cualquiera
 * confirmaría sus reservas gratis con el id de la transacción de otro.
 *
 * La pasarela se sustituye por un doble —consultarla de verdad exigiría red— pero solo eso: el
 * resto del camino (endpoint, permisos, estados, idempotencia) es el de producción.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, PaymentSyncIT.StubbedGateway.class})
class PaymentSyncIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);
    private static final long RATE_COP = 60_000;

    /** Lo que "responde Wompi" en cada test. Estático porque el bean lo crea Spring, no el test. */
    static ProviderTransaction respuesta;

    @TestConfiguration
    static class StubbedGateway {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        PaymentProvider stubProvider() {
            return new PaymentProvider() {
                @Override
                public String name() {
                    return "WOMPI";
                }

                @Override
                public long minimumChargeCop() {
                    return 1500;
                }

                @Override
                public PaymentIntent createIntent(Payment payment, String reference, String returnUrl) {
                    return new PaymentIntent("WOMPI", reference,
                            "https://checkout.example/" + reference, payment.getChargedCop() * 100);
                }

                @Override
                public ProviderEvent parseWebhook(String rawBody, Map<String, String> headers) {
                    throw new UnsupportedOperationException("este test no usa webhooks");
                }

                @Override
                public ProviderTransaction fetchTransaction(String transactionId) {
                    return respuesta;
                }
            };
        }
    }

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private AvailabilityRuleRepository rules;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private PaymentRepository payments;

    private User maria;
    private Session anaSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);

        ProfessorProfile profile = new ProfessorProfile(maria);
        profile.changeRate(RATE_COP);
        profile.publish();
        profiles.save(profile);
        approveTeacher(maria.getId());

        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");
    }

    @Test
    void aLostWebhookIsRecoveredWhenTheStudentReturnsFromTheGateway() {
        UUID bookingId = book(9);
        Payment payment = payments.findByBookingId(bookingId).orElseThrow();

        respuesta = new ProviderTransaction("txn-real", payment.getProviderReference(),
                ProviderTransactionStatus.APPROVED, payment.getChargedCop() * 100);

        ResponseEntity<PaymentStatusResponse> response = get(
                BOOKINGS + "/" + bookingId + "/payment?transactionId=txn-real",
                anaSession, PaymentStatusResponse.class);

        assertThat(response.getBody().paymentStatus()).isEqualTo("PAID");
        assertThat(response.getBody().bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    /** El id lo pone el navegador. Si no corresponde a este pago, no confirma nada. */
    @Test
    void aTransactionThatBelongsToAnotherPaymentConfirmsNothing() {
        UUID bookingId = book(9);

        respuesta = new ProviderTransaction("txn-ajena", "ORION-referencia-de-otro",
                ProviderTransactionStatus.APPROVED, RATE_COP * 100);

        ResponseEntity<PaymentStatusResponse> response = get(
                BOOKINGS + "/" + bookingId + "/payment?transactionId=txn-ajena",
                anaSession, PaymentStatusResponse.class);

        assertThat(response.getBody().paymentStatus()).isEqualTo("PENDING");
        assertThat(response.getBody().bookingStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void aDeclinedTransactionReleasesTheSlot() {
        UUID bookingId = book(9);
        Payment payment = payments.findByBookingId(bookingId).orElseThrow();

        respuesta = new ProviderTransaction("txn-rechazada", payment.getProviderReference(),
                ProviderTransactionStatus.DECLINED, payment.getChargedCop() * 100);

        get(BOOKINGS + "/" + bookingId + "/payment?transactionId=txn-rechazada",
                anaSession, PaymentStatusResponse.class);

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CANCELLED);
    }

    /** Consultar sin id de transacción es solo leer: no puede cambiar nada. */
    @Test
    void pollingWithoutATransactionIdChangesNothing() {
        UUID bookingId = book(9);
        respuesta = null;

        ResponseEntity<PaymentStatusResponse> response = get(
                BOOKINGS + "/" + bookingId + "/payment", anaSession, PaymentStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().paymentStatus()).isEqualTo("PENDING");
        assertThat(response.getBody().checkoutUrl()).isNotNull();
    }

    private UUID book(int hour) {
        OffsetDateTime at = ZonedDateTime
                .of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        ResponseEntity<BookingResponse> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(maria.getId(), at, "VIRTUAL", null, null, null),
                BookingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }
}
