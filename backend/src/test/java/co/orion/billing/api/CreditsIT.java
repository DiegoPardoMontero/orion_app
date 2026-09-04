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
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.application.PaymentExpiryJob;
import co.orion.billing.domain.CreditReason;
import co.orion.billing.domain.StudentCredit;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.billing.persistence.StudentCreditRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.api.BookingResponse;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.shared.time.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El saldo del estudiante: cómo se gasta, en qué orden y cómo vuelve cuando la reserva no prospera.
 * La clase de María vale 60 000 y la comisión es del 20 %.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, CreditsIT.FrozenClockConfiguration.class})
class CreditsIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);
    private static final long RATE_COP = 60_000;

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
    private StudentCreditRepository credits;

    @Autowired
    private PaymentExpiryJob expiryJob;

    @Autowired
    private JdbcTemplate jdbc;

    private User ana;
    private User maria;
    private Session anaSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
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
    void aPartialCreditReducesOnlyWhatTheGatewayCharges() {
        grant(20_000, null);

        BookingResponse booking = book(9);

        assertThat(booking.payment().amountCop()).isEqualTo(RATE_COP);
        assertThat(booking.payment().creditAppliedCop()).isEqualTo(20_000);
        assertThat(booking.payment().chargedCop()).isEqualTo(40_000);
        assertThat(booking.payment().checkoutUrl()).contains("amount-in-cents=4000000");

        // La comisión se calcula sobre el PRECIO, no sobre lo cobrado: el crédito lo pone Orión,
        // no el profesor. 20 % de 60 000 = 12 000, y el profesor se gana 48 000 completos.
        var payment = payments.findByBookingId(booking.id()).orElseThrow();
        assertThat(payment.getCommissionCop()).isEqualTo(12_000);
        assertThat(payment.getProfessorEarningsCop()).isEqualTo(48_000);
    }

    @Test
    void aCreditThatCoversTheWholeClassConfirmsItWithoutTheGateway() {
        grant(RATE_COP, null);

        BookingResponse booking = book(9);

        assertThat(booking.payment().chargedCop()).isZero();
        assertThat(booking.payment().checkoutUrl()).isNull();
        // Sin pasarela que esperar, la clase existe desde ya.
        assertThat(booking.status()).isEqualTo("CONFIRMED");
        assertThat(bookings.findById(booking.id()).orElseThrow().getMeetingLink()).isNotNull();
        assertThat(balance()).isZero();
    }

    /** FIFO por vencimiento: se gasta primero lo que primero se pierde. */
    @Test
    void creditsAreConsumedInOrderOfExpiry() {
        StudentCredit later = grant(30_000, FROZEN_NOW.plusSeconds(60 * 60 * 24 * 30));
        StudentCredit sooner = grant(30_000, FROZEN_NOW.plusSeconds(60 * 60 * 24 * 3));
        StudentCredit noExpiry = grant(30_000, null);

        book(9);   // consume 60 000 de los 90 000 disponibles

        assertThat(credits.findById(sooner.getId()).orElseThrow().getRemainingCop()).isZero();
        assertThat(credits.findById(later.getId()).orElseThrow().getRemainingCop()).isZero();
        // El que no vence nunca es el último en gastarse: es el que menos urge.
        assertThat(credits.findById(noExpiry.getId()).orElseThrow().getRemainingCop())
                .isEqualTo(30_000);
    }

    @Test
    void anExpiredCreditIsNotSpent() {
        grant(50_000, FROZEN_NOW.minusSeconds(1));

        BookingResponse booking = book(9);

        assertThat(booking.payment().creditAppliedCop()).isZero();
        assertThat(booking.payment().chargedCop()).isEqualTo(RATE_COP);
    }

    /**
     * Sin este ajuste el crédito dejaría un cobro de 100 pesos, que Wompi no acepta: la reserva
     * sería imposible de pagar. Se aplica MENOS crédito y el estudiante conserva la diferencia.
     */
    @Test
    void aRemainderBelowTheGatewayMinimumIsAvoidedByApplyingLessCredit() {
        grant(RATE_COP - 100, null);

        BookingResponse booking = book(9);

        assertThat(booking.payment().chargedCop()).isEqualTo(1_500);
        assertThat(booking.payment().creditAppliedCop()).isEqualTo(RATE_COP - 1_500);
        assertThat(balance()).isEqualTo(1_400);
    }

    /**
     * Una reserva que vence tiene que devolver el crédito a SUS filas, con su vencimiento original.
     * Devolverlo como un crédito nuevo le regalaría al estudiante una fecha que no tenía.
     */
    @Test
    void anExpiredBookingReturnsTheCreditToItsOriginalRow() {
        Instant expiry = FROZEN_NOW.plusSeconds(60 * 60 * 24 * 3);
        StudentCredit credit = grant(20_000, expiry);

        BookingResponse booking = book(9);
        assertThat(credits.findById(credit.getId()).orElseThrow().getRemainingCop()).isZero();

        jdbc.update("update bookings set expires_at = ? where id = ?",
                java.sql.Timestamp.from(FROZEN_NOW.minusSeconds(60)), booking.id());
        expiryJob.expireOverduePayments();

        StudentCredit restored = credits.findById(credit.getId()).orElseThrow();
        assertThat(restored.getRemainingCop()).isEqualTo(20_000);
        assertThat(restored.getExpiresAt()).isEqualTo(expiry);
        assertThat(bookings.findById(booking.id()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
        // Y no se duplicó: sigue habiendo un solo crédito.
        assertThat(credits.count()).isEqualTo(1);
    }

    /** Dos reservas seguidas no pueden gastar el mismo saldo dos veces. */
    @Test
    void theSameCreditCannotBeSpentTwice() {
        grant(50_000, null);

        BookingResponse first = book(9);
        BookingResponse second = book(10);

        assertThat(first.payment().creditAppliedCop()).isEqualTo(50_000);
        assertThat(second.payment().creditAppliedCop()).isZero();
        assertThat(second.payment().chargedCop()).isEqualTo(RATE_COP);
        assertThat(balance()).isZero();
    }

    @Test
    void theStudentSeesTheirBalanceAndItsDetail() {
        grant(20_000, null);

        ResponseEntity<CreditBalanceResponse> response = get(
                "/api/v1/me/credits", anaSession, CreditBalanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().balanceCop()).isEqualTo(20_000);
        assertThat(response.getBody().credits()).hasSize(1);
        assertThat(response.getBody().credits().get(0).reason()).isEqualTo("ADMIN_ADJUSTMENT");
    }

    private StudentCredit grant(long amountCop, Instant expiresAt) {
        return credits.saveAndFlush(new StudentCredit(
                ana.getId(), amountCop, CreditReason.ADMIN_ADJUSTMENT, null, expiresAt, null));
    }

    private long balance() {
        return get("/api/v1/me/credits", anaSession, CreditBalanceResponse.class)
                .getBody().balanceCop();
    }

    private BookingResponse book(int hour) {
        OffsetDateTime at = ZonedDateTime
                .of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        ResponseEntity<BookingResponse> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(maria.getId(), at, "VIRTUAL", null, null, null),
                BookingResponse.class);
        assertThat(response.getStatusCode())
                .as("respuesta: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
