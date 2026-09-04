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

import co.orion.TestcontainersConfiguration;
import co.orion.billing.domain.PaymentStatus;
import co.orion.billing.persistence.PaymentRepository;
import co.orion.identity.api.AdminRateRequest;
import co.orion.identity.api.RateBreakdownResponse;
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
 * La clase gratuita: tarifa 0, reserva que se confirma sin pasarela. Es el camino que permite
 * probar el flujo entero en producción sin mover dinero, así que conviene que esté clavado —
 * sobre todo la parte de quién puede poner ese 0.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, FreeClassIT.FrozenClockConfiguration.class})
class FreeClassIT extends ApiIntegrationSupport {

    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);

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

    private User maria;
    private Session anaSession;
    private Session mariaSession;
    private Session adminSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        ProfessorProfile profile = new ProfessorProfile(maria);
        profile.changeRate(0L);
        profile.publish();
        profiles.save(profile);
        approveTeacher(maria.getId());

        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
        adminSession = login("admin@orion.test");
    }

    @Test
    void aFreeClassIsConfirmedOnTheSpotWithNoGatewayToVisit() {
        ResponseEntity<BookingResponse> response = post(
                "/api/v1/bookings", anaSession, bookingRequest(9), BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BookingResponse body = response.getBody();
        assertThat(body.status()).isEqualTo(BookingStatus.CONFIRMED.name());
        // Lo que prueba que no hay pasarela de por medio: no hay a dónde mandar al estudiante.
        assertThat(body.payment().checkoutUrl()).isNull();
        assertThat(body.payment().chargedCop()).isZero();
    }

    /** El pago existe aunque valga 0: la contabilidad no puede tener huecos. */
    @Test
    void theBookkeepingEntryStillExistsAndIsClosedAtZero() {
        UUID bookingId = post("/api/v1/bookings", anaSession, bookingRequest(9), BookingResponse.class)
                .getBody().id();

        var payment = payments.findByBookingId(bookingId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmountCop()).isZero();
        assertThat(payment.getCommissionCop()).isZero();
        assertThat(payment.getProfessorEarningsCop()).isZero();
    }

    @Test
    void anAdminCanSetTheFreeRate() {
        ResponseEntity<RateBreakdownResponse> response = put(
                "/api/v1/admin/professors/" + maria.getId() + "/rate",
                adminSession, new AdminRateRequest(0L), RateBreakdownResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().hourlyRateCop()).isZero();
    }

    /**
     * El profesor no: su formulario conserva el piso de 20.000. Sin esto, un cero de más al teclear
     * lo dejaría trabajando gratis sin enterarse.
     */
    @Test
    void aProfessorCannotZeroOutTheirOwnRate() {
        ResponseEntity<String> response = put(
                "/api/v1/me/profile/rate", mariaSession,
                java.util.Map.of("hourlyRateCop", 0), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Entre 1 y 19.999 no es una clase barata, es un error de tecleo (y no llega al mínimo de
     * Wompi). Se rechaza con 422 y un mensaje legible, no con el 500 de una constraint violada.
     */
    @Test
    void anAmountBetweenZeroAndTheFloorIsStillRejected() {
        ResponseEntity<String> response = put(
                "/api/v1/admin/professors/" + maria.getId() + "/rate",
                adminSession, java.util.Map.of("hourlyRateCop", 5_000), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    private CreateBookingRequest bookingRequest(int hour) {
        OffsetDateTime at = ZonedDateTime
                .of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
        return new CreateBookingRequest(maria.getId(), at, "VIRTUAL", null, null, null);
    }
}
