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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.application.PaymentExpiryJob;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.persistence.PaymentEventRepository;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.api.BookingResponse;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.api.SlotsResponse;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;
import co.orion.support.WompiWebhooks;

/**
 * El recaudo de punta a punta: el webhook de Wompi, su firma, su idempotencia y lo que pasa con el
 * cupo en cada desenlace. Reloj congelado en el lunes 2026-07-13 a las 12:00 de Bogotá.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, PaymentFlowIT.FrozenClockConfiguration.class})
class PaymentFlowIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final String WEBHOOK = "/api/v1/webhooks/payments/wompi";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);
    private static final long RATE_COP = 60_000;
    private static final long TIMESTAMP = 1_756_000_000L;

    @TestConfiguration
    static class FrozenClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FROZEN_NOW, ZoneOffset.UTC);
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

    @Autowired
    private PaymentEventRepository paymentEvents;

    @Autowired
    private PaymentExpiryJob expiryJob;

    @Autowired
    private JdbcTemplate jdbc;

    private User ana;
    private User carlos;
    private User maria;
    private Session anaSession;
    private Session carlosSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        carlos = createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);

        ProfessorProfile profile = new ProfessorProfile(maria);
        profile.changeRate(RATE_COP);
        profile.publish();
        profiles.save(profile);
        approveTeacher(maria.getId());

        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");
        carlosSession = login("carlos@orion.test");
    }

    @Test
    void anApprovedPaymentConfirmsTheClassAndOpensTheAccountingEntry() {
        UUID bookingId = book(anaSession, 9);

        approvePayment(bookingId);

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        var payment = payments.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmountCop()).isEqualTo(RATE_COP);
        // Comisión del 20 % (platform_settings.commission_rate_bps = 2000).
        assertThat(payment.getCommissionCop()).isEqualTo(12_000);
        assertThat(payment.getProfessorEarningsCop()).isEqualTo(48_000);
        assertThat(payment.getCommissionCop() + payment.getProfessorEarningsCop())
                .isEqualTo(payment.getAmountCop());
    }

    /** Un webhook sin verificar es un endpoint público que confirma reservas gratis. */
    @Test
    void aWebhookWithAnInvalidSignatureIsRejectedAndChangesNothing() {
        UUID bookingId = book(anaSession, 9);
        var payment = payments.findByBookingId(bookingId).orElseThrow();

        ResponseEntity<Map> response = postWebhook(WompiWebhooks.tampered(
                "txn-falsa", payment.getProviderReference(), "APPROVED",
                payment.getChargedCop() * 100, TIMESTAMP));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        // Ni siquiera queda rastro: la firma se verifica ANTES de tocar la base.
        assertThat(paymentEvents.count()).isZero();
    }

    /** La pasarela reenvía eventos: es normal, y un doble procesamiento no puede duplicar nada. */
    @Test
    void aResentWebhookIsProcessedOnlyOnce() {
        UUID bookingId = book(anaSession, 9);
        var payment = payments.findByBookingId(bookingId).orElseThrow();
        String event = WompiWebhooks.signed("txn-1", payment.getProviderReference(),
                "APPROVED", payment.getChargedCop() * 100, TIMESTAMP);

        ResponseEntity<Map> first = postWebhook(event);
        ResponseEntity<Map> second = postWebhook(event);

        assertThat(first.getBody().get("status")).isEqualTo("processed");
        assertThat(second.getBody().get("status")).isEqualTo("duplicate");
        // Para Wompi el reenvío es un éxito: si respondiéramos error lo reintentaría para siempre.
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentEvents.count()).isEqualTo(1);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    /** Una prueba desde el panel de Wompi no puede tumbar el endpoint ni quedar sin registrar. */
    @Test
    void aWebhookForAnUnknownTransactionIsRecordedAndDiscarded() {
        ResponseEntity<Map> response = postWebhook(WompiWebhooks.signed(
                "txn-desconocida", "ORION-referencia-que-no-existe", "APPROVED", 5_000_000, TIMESTAMP));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentEvents.count()).isEqualTo(1);
        assertThat(paymentEvents.findAll().get(0).getPaymentId()).isNull();
    }

    /** Un pago por menos de lo debido es un incidente, no una clase. */
    @Test
    void anApprovedWebhookWithTheWrongAmountDoesNotConfirmTheClass() {
        UUID bookingId = book(anaSession, 9);
        var payment = payments.findByBookingId(bookingId).orElseThrow();

        postWebhook(WompiWebhooks.signed("txn-2", payment.getProviderReference(),
                "APPROVED", 100, TIMESTAMP));

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void aDeclinedPaymentFreesTheSlotForAnotherStudent() {
        UUID bookingId = book(anaSession, 9);
        assertThat(availableSlots()).hasSize(2);

        declinePayment(bookingId);

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CANCELLED);
        assertThat(availableSlots()).hasSize(3);
        // Y el cupo se puede volver a reservar de verdad, no solo "aparece libre".
        assertThat(book(carlosSession, 9)).isNotNull();
    }

    /**
     * El caso que de verdad importa del job: el estudiante abre PSE, cierra la pestaña y la pasarela
     * nunca responde. Sin el barrido ese cupo quedaría bloqueado para siempre.
     */
    @Test
    void anExpiredPendingBookingReleasesTheSlotAndAnotherStudentCanTakeIt() {
        UUID bookingId = book(anaSession, 9);
        jdbc.update("update bookings set expires_at = ? where id = ?",
                java.sql.Timestamp.from(FROZEN_NOW.minusSeconds(60)), bookingId);

        expiryJob.expireOverduePayments();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CANCELLED);

        UUID retaken = book(carlosSession, 9);
        assertThat(bookings.findById(retaken).orElseThrow().getStudentId()).isEqualTo(carlos.getId());
    }

    /** El estudiante consulta el estado desde la pantalla de retorno; el de otro no existe para él. */
    @Test
    void theStudentPollsTheirOwnPaymentAndNobodyElses() {
        UUID bookingId = book(anaSession, 9);

        ResponseEntity<PaymentStatusResponse> mine = get(
                BOOKINGS + "/" + bookingId + "/payment", anaSession, PaymentStatusResponse.class);
        assertThat(mine.getBody().paymentStatus()).isEqualTo("PENDING");
        assertThat(mine.getBody().bookingStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(mine.getBody().chargedCop()).isEqualTo(RATE_COP);

        ResponseEntity<Map> theirs = get(
                BOOKINGS + "/" + bookingId + "/payment", carlosSession, Map.class);
        assertThat(theirs.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Un profesor sin tarifa no se puede reservar: no habría precio que cobrar. */
    @Test
    void aProfessorWithoutARateCannotBeBooked() {
        ProfessorProfile profile = profiles.findById(maria.getId()).orElseThrow();
        profile.changeRate(null);
        profiles.save(profile);

        ResponseEntity<Map> response = post(BOOKINGS, anaSession, bookingRequest(9), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("tarifa");
    }

    private UUID book(Session session, int hour) {
        ResponseEntity<BookingResponse> response = post(
                BOOKINGS, session, bookingRequest(hour), BookingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    private CreateBookingRequest bookingRequest(int hour) {
        OffsetDateTime at = ZonedDateTime
                .of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        return new CreateBookingRequest(maria.getId(), at, "VIRTUAL", null, null);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> postWebhook(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity(WEBHOOK, new HttpEntity<>(body, headers), Map.class);
    }

    private java.util.List<?> availableSlots() {
        return get("/api/v1/professors/" + maria.getId() + "/slots?from=2026-07-15&to=2026-07-15",
                anaSession, SlotsResponse.class).getBody().slots();
    }
}
