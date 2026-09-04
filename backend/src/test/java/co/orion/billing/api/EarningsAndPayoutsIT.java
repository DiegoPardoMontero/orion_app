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
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.billing.persistence.StudentCreditRepository;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.api.BookingResponse;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.api.RecordAttendanceRequest;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.shared.time.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * Del dinero retenido a la transferencia: la clase se dicta, la plata se libera, el admin genera la
 * liquidación y la marca pagada. Y quién puede ver qué, que en este bloque es media funcionalidad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, EarningsAndPayoutsIT.FrozenClockConfiguration.class})
class EarningsAndPayoutsIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);
    private static final long RATE_COP = 60_000;
    private static final long COMMISSION_COP = 12_000;
    private static final long EARNINGS_COP = 48_000;

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
    private JdbcTemplate jdbc;

    private User ana;
    private User maria;
    private User juan;
    private Session anaSession;
    private Session mariaSession;
    private Session juanSession;
    private Session adminSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        publish(maria);
        publish(juan);

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
        juanSession = login("juan@orion.test");
        adminSession = login("admin@orion.test");
    }

    private void publish(User professor) {
        ProfessorProfile profile = new ProfessorProfile(professor);
        profile.changeRate(RATE_COP);
        profile.publish();
        profiles.save(profile);
        approveTeacher(professor.getId());
        rules.save(new AvailabilityRule(professor.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));
    }

    @Test
    void moneyIsHeldUntilTheClassHappensAndOnlyThenBecomesPayable() {
        UUID bookingId = bookAndPay(maria, 9);

        EarningsResponse held = earnings(mariaSession);
        assertThat(held.heldCop()).isEqualTo(EARNINGS_COP);
        assertThat(held.payableCop()).isZero();

        recordAttendance(bookingId, mariaSession);

        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.RELEASED);
        EarningsResponse payable = earnings(mariaSession);
        assertThat(payable.heldCop()).isZero();
        assertThat(payable.payableCop()).isEqualTo(EARNINGS_COP);
        assertThat(payable.transferredCop()).isZero();
    }

    @Test
    void aProfessorSeesTheCommissionOfTheirOwnClassesAndNothingOfAnybodyElses() {
        bookAndPay(maria, 9);

        EarningsResponse mine = earnings(mariaSession);
        assertThat(mine.lines()).hasSize(1);
        assertThat(mine.lines().get(0).amountCop()).isEqualTo(RATE_COP);
        assertThat(mine.lines().get(0).commissionCop()).isEqualTo(COMMISSION_COP);
        assertThat(mine.lines().get(0).earningsCop()).isEqualTo(EARNINGS_COP);
        assertThat(mine.lines().get(0).studentName()).isEqualTo("Ana Ramírez");

        // Juan no ve un peso de las clases de María.
        EarningsResponse other = earnings(juanSession);
        assertThat(other.lines()).isEmpty();
        assertThat(other.totalCop()).isZero();
    }

    /** El estudiante compra una clase, no un servicio de intermediación: la comisión no es suya. */
    @Test
    void theStudentNeverSeesTheCommissionInAnyResponse() {
        UUID bookingId = bookAndPay(maria, 9);

        String history = get("/api/v1/me/payments", anaSession, String.class).getBody();
        String status = get(BOOKINGS + "/" + bookingId + "/payment", anaSession, String.class).getBody();

        assertThat(history).contains("amountCop").doesNotContain("commission");
        assertThat(status).doesNotContain("commission");

        // Y el endpoint de ganancias es del profesor: para la estudiante no existe.
        assertThat(get("/api/v1/me/earnings", anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theAdminGeneratesAPayoutMarksItPaidAndExportsIt() {
        UUID bookingId = bookAndPay(maria, 9);
        recordAttendance(bookingId, mariaSession);

        ResponseEntity<PayoutResponse[]> generated = post("/api/v1/admin/payouts/generate",
                adminSession, new GeneratePayoutsRequest(WEDNESDAY.minusDays(7), WEDNESDAY.plusDays(1)),
                PayoutResponse[].class);

        assertThat(generated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(generated.getBody()).hasSize(1);
        PayoutResponse payout = generated.getBody()[0];
        assertThat(payout.professorId()).isEqualTo(maria.getId());
        assertThat(payout.amountCop()).isEqualTo(EARNINGS_COP);
        assertThat(payout.status()).isEqualTo("PENDING");

        // Volver a generar el mismo período no paga la clase dos veces.
        ResponseEntity<PayoutResponse[]> again = post("/api/v1/admin/payouts/generate",
                adminSession, new GeneratePayoutsRequest(WEDNESDAY.minusDays(7), WEDNESDAY.plusDays(1)),
                PayoutResponse[].class);
        assertThat(again.getBody()).isEmpty();

        // Sin referencia de transferencia no se marca como pagada.
        assertThat(post("/api/v1/admin/payouts/" + payout.id() + "/mark-paid", adminSession,
                new MarkPayoutPaidRequest(""), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<PayoutResponse> paid = post(
                "/api/v1/admin/payouts/" + payout.id() + "/mark-paid", adminSession,
                new MarkPayoutPaidRequest("BANCOLOMBIA-99812"), PayoutResponse.class);
        assertThat(paid.getBody().status()).isEqualTo("PAID");
        assertThat(paid.getBody().reference()).isEqualTo("BANCOLOMBIA-99812");

        String csv = get("/api/v1/admin/payouts/" + payout.id() + "/export",
                adminSession, String.class).getBody();
        assertThat(csv)
                .contains("fecha_clase,estudiante,precio_cop,comision_cop,ganancia_cop")
                .contains("\"Ana Ramírez\",60000,12000,48000")
                .contains("TOTAL,,,,48000");

        // Y ya transferido, deja de estar "por cobrar" para el profesor.
        EarningsResponse after = earnings(mariaSession);
        assertThat(after.payableCop()).isZero();
        assertThat(after.transferredCop()).isEqualTo(EARNINGS_COP);
    }

    /** Una clase que no ocurrió no se le paga a nadie: la liquidación solo mira lo liberado. */
    @Test
    void aClassThatWasNeverGivenDoesNotEnterAPayout() {
        bookAndPay(maria, 9);   // pagada, pero sin registro de asistencia

        ResponseEntity<PayoutResponse[]> generated = post("/api/v1/admin/payouts/generate",
                adminSession, new GeneratePayoutsRequest(WEDNESDAY.minusDays(7), WEDNESDAY.plusDays(1)),
                PayoutResponse[].class);

        assertThat(generated.getBody()).isEmpty();
    }

    /** Si el profesor cancela, el estudiante recupera el valor completo de la clase como saldo. */
    @Test
    void aProfessorCancellationTurnsThePaymentIntoCreditForTheStudent() {
        UUID bookingId = bookAndPay(maria, 9);

        ResponseEntity<BookingResponse> cancelled = post(
                BOOKINGS + "/" + bookingId + "/cancel", mariaSession, null, BookingResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(payments.findByBookingId(bookingId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);

        assertThat(credits.findAll()).hasSize(1);
        assertThat(credits.findAll().get(0).getRemainingCop()).isEqualTo(RATE_COP);
        assertThat(credits.findAll().get(0).getReason().name()).isEqualTo("CANCELLED_BY_PROFESSOR");
    }

    @Test
    void onlyTheAdminReachesTheReconciliationScreen() {
        assertThat(get("/api/v1/admin/payments", anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/payments", mariaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/payments", adminSession, AdminPaymentResponse[].class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UUID bookAndPay(User professor, int hour) {
        OffsetDateTime at = ZonedDateTime
                .of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        ResponseEntity<BookingResponse> response = post(BOOKINGS, anaSession,
                new CreateBookingRequest(professor.getId(), at, "VIRTUAL", null, null, null),
                BookingResponse.class);
        assertThat(response.getStatusCode())
                .as("respuesta: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        approvePayment(response.getBody().id());
        return response.getBody().id();
    }

    /**
     * La asistencia solo se registra sobre una clase que ya terminó, y el reloj está congelado: se
     * mueve la CLASE al pasado en vez del reloj. Es el mismo hecho visto desde el otro lado.
     */
    private void recordAttendance(UUID bookingId, Session professorSession) {
        jdbc.update("update bookings set starts_at = ?, ends_at = ? where id = ?",
                java.sql.Timestamp.from(FROZEN_NOW.minusSeconds(7200)),
                java.sql.Timestamp.from(FROZEN_NOW.minusSeconds(3600)),
                bookingId);

        ResponseEntity<Map> response = post(BOOKINGS + "/" + bookingId + "/attendance",
                professorSession, new RecordAttendanceRequest(true, null), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private EarningsResponse earnings(Session session) {
        return get("/api/v1/me/earnings?from=2026-07-01&to=2026-07-31", session,
                EarningsResponse.class).getBody();
    }
}
