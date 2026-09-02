package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Duration;
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

import co.orion.scheduling.TestBookings;
import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.api.AdminBookingsController.MetricsResponse;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/** Reloj congelado en el lunes 2026-07-13 a las 12:00 de Bogotá (= 17:00 UTC). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, AdminBookingsIT.FrozenClockConfiguration.class})
class AdminBookingsIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/admin/bookings";
    private static final String METRICS = "/api/v1/admin/metrics";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");

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
    private User carlos;
    private User maria;
    private User juan;
    private User admin;
    private Session adminSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        carlos = createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        admin = createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        adminSession = login("admin@orion.test");
    }

    private Booking booking(User student, User professor, Instant startsAt, User createdBy) {
        return bookings.save(TestBookings.confirmed(student.getId(), professor.getId(), startsAt,
                BookingModality.VIRTUAL, null, createdBy.getId()));
    }

    @Test
    void listsEveryBookingNewestFirstWithBothNames() {
        booking(ana, maria, FROZEN_NOW.plus(Duration.ofDays(1)), ana);
        booking(carlos, juan, FROZEN_NOW.plus(Duration.ofDays(3)), carlos);

        ResponseEntity<AdminBookingResponse[]> response = get(
                BOOKINGS, adminSession, AdminBookingResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        // Orden descendente: la más lejana primero.
        assertThat(response.getBody()[0].studentName()).isEqualTo("Carlos Peña");
        assertThat(response.getBody()[0].professorName()).isEqualTo("Juan Torres");
        assertThat(response.getBody()[1].studentName()).isEqualTo("Ana Ramírez");
    }

    @Test
    void filtersByProfessor() {
        booking(ana, maria, FROZEN_NOW.plus(Duration.ofDays(1)), ana);
        booking(carlos, juan, FROZEN_NOW.plus(Duration.ofDays(2)), carlos);

        ResponseEntity<AdminBookingResponse[]> response = get(
                BOOKINGS + "?professorId=" + maria.getId(), adminSession, AdminBookingResponse[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].professorName()).isEqualTo("María Gómez");
    }

    @Test
    void filtersByStatus() {
        Booking cancelada = booking(ana, maria, FROZEN_NOW.plus(Duration.ofDays(1)), ana);
        jdbc.update("update bookings set status = 'CANCELLED_BY_STUDENT' where id = ?", cancelada.getId());
        booking(carlos, maria, FROZEN_NOW.plus(Duration.ofDays(2)), carlos);

        ResponseEntity<AdminBookingResponse[]> confirmadas = get(
                BOOKINGS + "?status=CONFIRMED", adminSession, AdminBookingResponse[].class);
        assertThat(confirmadas.getBody()).hasSize(1);
        assertThat(confirmadas.getBody()[0].studentName()).isEqualTo("Carlos Peña");

        ResponseEntity<AdminBookingResponse[]> canceladas = get(
                BOOKINGS + "?status=CANCELLED_BY_STUDENT", adminSession, AdminBookingResponse[].class);
        assertThat(canceladas.getBody()).hasSize(1);
    }

    @Test
    void filtersByDateRangeUsingBogotaDays() {
        // 2026-07-14 a las 20:00 de Bogotá (= 01:00 UTC del día 15). Filtrando por el día 14 en
        // Bogotá tiene que salir, aunque en UTC ya sea día 15.
        booking(ana, maria, Instant.parse("2026-07-15T01:00:00Z"), ana);

        ResponseEntity<AdminBookingResponse[]> response = get(
                BOOKINGS + "?from=2026-07-14&to=2026-07-14", adminSession, AdminBookingResponse[].class);

        assertThat(response.getBody()).hasSize(1);

        ResponseEntity<AdminBookingResponse[]> otroDia = get(
                BOOKINGS + "?from=2026-07-15&to=2026-07-15", adminSession, AdminBookingResponse[].class);
        assertThat(otroDia.getBody()).isEmpty();
    }

    @Test
    void marksWhichBookingsWereSelfService() {
        booking(ana, maria, FROZEN_NOW.plus(Duration.ofDays(1)), ana);      // la hizo la estudiante
        booking(carlos, maria, FROZEN_NOW.plus(Duration.ofDays(2)), admin); // la hizo el admin

        ResponseEntity<AdminBookingResponse[]> response = get(
                BOOKINGS, adminSession, AdminBookingResponse[].class);

        assertThat(response.getBody()[0].selfService()).isFalse(); // Carlos, creada por el admin
        assertThat(response.getBody()[1].selfService()).isTrue();  // Ana, autoservicio
    }

    @Test
    void metricsCountRecentBookingsAndTheSelfServiceShare() {
        booking(ana, maria, FROZEN_NOW.plus(Duration.ofDays(1)), ana);
        booking(carlos, maria, FROZEN_NOW.plus(Duration.ofDays(2)), admin);
        booking(carlos, juan, FROZEN_NOW.plus(Duration.ofDays(3)), carlos);
        booking(ana, juan, FROZEN_NOW.plus(Duration.ofDays(4)), ana);

        // Una creada hace 10 días: cuenta para el % histórico pero no para "últimos 7 días".
        Booking vieja = booking(ana, maria, FROZEN_NOW.plus(Duration.ofDays(9)), admin);
        jdbc.update("update bookings set created_at = ? where id = ?",
                java.sql.Timestamp.from(FROZEN_NOW.minus(Duration.ofDays(10))), vieja.getId());

        ResponseEntity<MetricsResponse> response = get(METRICS, adminSession, MetricsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().bookingsLast7Days()).isEqualTo(4);
        // 3 de 5 las hizo el propio estudiante.
        assertThat(response.getBody().selfServicePctAllTime()).isCloseTo(60.0, within(0.01));
    }

    @Test
    void metricsOnAnEmptyDatabaseAreZeroAndNotAnError() {
        ResponseEntity<MetricsResponse> response = get(METRICS, adminSession, MetricsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().bookingsLast7Days()).isZero();
        // Sin reservas no hay división: 0 %, no un NaN ni un 500.
        assertThat(response.getBody().selfServicePctAllTime()).isZero();
    }

    @Test
    void aProfessorCannotUseTheAdminEndpoints() {
        Session mariaSession = login("maria@orion.test");

        assertThat(get(BOOKINGS, mariaSession, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(METRICS, mariaSession, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
