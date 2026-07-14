package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

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
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/** Reloj congelado en el lunes 2026-07-13 a las 12:00 de Bogotá (= 17:00 UTC). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, MyBookingsIT.FrozenClockConfiguration.class})
class MyBookingsIT extends ApiIntegrationSupport {

    private static final String MY_BOOKINGS = "/api/v1/me/bookings";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");

    /** Dentro de 3 días: cancelable de sobra. */
    private static final Instant FAR_FUTURE = FROZEN_NOW.plus(java.time.Duration.ofDays(3));
    /** Dentro de 5 horas: ya no cancelable (faltan menos de 24 h). */
    private static final Instant SOON = FROZEN_NOW.plus(java.time.Duration.ofHours(5));
    /** Hace 2 días: pasada. */
    private static final Instant PAST = FROZEN_NOW.minus(java.time.Duration.ofDays(2));

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
    private JdbcTemplate jdbc;

    private User ana;
    private User maria;
    private Session anaSession;
    private Session mariaSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana.changeWhatsappPhone("+573001112233");
        maria.changeWhatsappPhone("+573009998877");
        users.save(ana);
        users.save(maria);

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
    }

    private Booking booking(Instant startsAt) {
        return bookings.save(new Booking(ana.getId(), maria.getId(), startsAt,
                startsAt.plus(java.time.Duration.ofHours(1)),
                BookingModality.VIRTUAL, "Meet", ana.getId()));
    }

    @Test
    void theStudentSeesTheProfessorAsCounterpart() {
        booking(FAR_FUTURE);

        ResponseEntity<MyBookingResponse[]> response = get(MY_BOOKINGS, anaSession, MyBookingResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        MyBookingResponse item = response.getBody()[0];
        assertThat(item.counterpart().fullName()).isEqualTo("María Gómez");
        assertThat(item.counterpart().whatsappPhone()).isEqualTo("+573009998877");
        assertThat(item.canCancel()).isTrue();
        assertThat(item.locationNote()).isEqualTo("Meet");
    }

    @Test
    void theProfessorSeesTheStudentAsCounterpart() {
        booking(FAR_FUTURE);

        ResponseEntity<MyBookingResponse[]> response = get(MY_BOOKINGS, mariaSession, MyBookingResponse[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].counterpart().fullName()).isEqualTo("Ana Ramírez");
        assertThat(response.getBody()[0].counterpart().whatsappPhone()).isEqualTo("+573001112233");
    }

    @Test
    void aClassLessThanTwentyFourHoursAwayCannotBeCancelled() {
        booking(SOON);

        ResponseEntity<MyBookingResponse[]> response = get(MY_BOOKINGS, anaSession, MyBookingResponse[].class);

        // Sigue siendo una clase próxima (aparece en el listado), pero ya no es cancelable.
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].canCancel()).isFalse();
    }

    @Test
    void exactlyTwentyFourHoursAheadIsStillCancellable() {
        booking(FROZEN_NOW.plus(java.time.Duration.ofHours(24)));

        ResponseEntity<MyBookingResponse[]> response = get(MY_BOOKINGS, anaSession, MyBookingResponse[].class);

        // La frontera es inclusiva: "24 horas o más" sí se puede cancelar.
        assertThat(response.getBody()[0].canCancel()).isTrue();
    }

    @Test
    void upcomingOnlyShowsConfirmedClassesThatHaveNotStarted() {
        booking(FAR_FUTURE);
        booking(PAST);
        Booking cancelled = booking(FROZEN_NOW.plus(java.time.Duration.ofDays(4)));
        jdbc.update("update bookings set status = 'CANCELLED_BY_STUDENT' where id = ?", cancelled.getId());

        ResponseEntity<MyBookingResponse[]> response = get(MY_BOOKINGS, anaSession, MyBookingResponse[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].startsAt().toInstant()).isEqualTo(FAR_FUTURE);
    }

    @Test
    void pastShowsWhatAlreadyHappenedOrIsTerminalNewestFirst() {
        booking(FAR_FUTURE);
        booking(PAST);
        Booking cancelled = booking(FROZEN_NOW.plus(java.time.Duration.ofDays(4)));
        jdbc.update("update bookings set status = 'CANCELLED_BY_STUDENT' where id = ?", cancelled.getId());

        ResponseEntity<MyBookingResponse[]> response = get(
                MY_BOOKINGS + "?scope=past", anaSession, MyBookingResponse[].class);

        // La cancelada (aunque sea futura) y la que ya ocurrió; orden descendente por fecha.
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()[0].status()).isEqualTo("CANCELLED_BY_STUDENT");
        assertThat(response.getBody()[1].startsAt().toInstant()).isEqualTo(PAST);
    }

    @Test
    void aTerminalBookingIsNeverCancellable() {
        Booking cancelled = booking(FROZEN_NOW.plus(java.time.Duration.ofDays(4)));
        jdbc.update("update bookings set status = 'CANCELLED_BY_STUDENT' where id = ?", cancelled.getId());

        ResponseEntity<MyBookingResponse[]> response = get(
                MY_BOOKINGS + "?scope=past", anaSession, MyBookingResponse[].class);

        // Falta mucho más de 24 h, pero ya está cancelada: canCancel mira el estado, no solo el reloj.
        assertThat(response.getBody()[0].canCancel()).isFalse();
    }

    @Test
    void anInvalidScopeIsRejected() {
        ResponseEntity<Map> response = get(MY_BOOKINGS + "?scope=maybe", anaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anAdminHasNoPersonalClasses() {
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        Session adminSession = login("admin@orion.test");

        ResponseEntity<Map> response = get(MY_BOOKINGS, adminSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
