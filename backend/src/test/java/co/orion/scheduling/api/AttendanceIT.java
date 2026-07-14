package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.AttendanceRecordRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/** Reloj congelado en el lunes 2026-07-13 a las 12:00 de Bogotá (= 17:00 UTC). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, AttendanceIT.FrozenClockConfiguration.class})
class AttendanceIT extends ApiIntegrationSupport {

    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    /** Empezó hace 3 horas y terminó hace 2: ya se puede registrar. */
    private static final Instant ALREADY_ENDED = FROZEN_NOW.minus(Duration.ofHours(3));
    /** Empieza mañana: todavía no. */
    private static final Instant FUTURE = FROZEN_NOW.plus(Duration.ofDays(1));

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
    private AttendanceRecordRepository attendance;

    @Autowired
    private JdbcTemplate jdbc;

    private User ana;
    private User maria;
    private User juan;
    private Session mariaSession;
    private Session anaSession;

    @BeforeEach
    void seed() {
        attendance.deleteAll();
        bookings.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);

        mariaSession = login("maria@orion.test");
        anaSession = login("ana@orion.test");
    }

    private Booking bookingOf(User professor, Instant startsAt) {
        return bookings.save(new Booking(ana.getId(), professor.getId(), startsAt,
                startsAt.plus(Duration.ofHours(1)), BookingModality.VIRTUAL, null, ana.getId()));
    }

    private String attendanceUrl(Booking booking) {
        return "/api/v1/bookings/" + booking.getId() + "/attendance";
    }

    @Test
    void aPresentStudentCompletesTheClass() {
        Booking booking = bookingOf(maria, ALREADY_ENDED);

        ResponseEntity<AttendanceResponse> response = post(
                attendanceUrl(booking), mariaSession,
                new RecordAttendanceRequest(true, "Muy buena participación"),
                AttendanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().present()).isTrue();
        assertThat(response.getBody().bookingStatus()).isEqualTo("COMPLETED");
        assertThat(response.getBody().notes()).isEqualTo("Muy buena participación");

        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.COMPLETED);
        assertThat(attendance.findByBookingId(booking.getId())).isPresent();
    }

    @Test
    void anAbsentStudentLeavesTheClassAsNoShow() {
        Booking booking = bookingOf(maria, ALREADY_ENDED);

        ResponseEntity<AttendanceResponse> response = post(
                attendanceUrl(booking), mariaSession,
                new RecordAttendanceRequest(false, "No se conectó"),
                AttendanceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().bookingStatus()).isEqualTo("NO_SHOW");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    void aClassThatHasNotEndedYetCannotBeRecorded() {
        Booking booking = bookingOf(maria, FUTURE);

        ResponseEntity<Map> response = post(
                attendanceUrl(booking), mariaSession, new RecordAttendanceRequest(true, null), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("aún no termina");
        assertThat(bookings.findById(booking.getId()).orElseThrow().isConfirmed()).isTrue();
    }

    @Test
    void recordingTwiceIsAConflict() {
        Booking booking = bookingOf(maria, ALREADY_ENDED);
        post(attendanceUrl(booking), mariaSession, new RecordAttendanceRequest(true, null),
                AttendanceResponse.class);

        ResponseEntity<Map> response = post(
                attendanceUrl(booking), mariaSession, new RecordAttendanceRequest(false, null), Map.class);

        // Tras el primer registro la reserva quedó COMPLETED: ya no es CONFIRMED, así que 409.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aCancelledClassCannotHaveAttendance() {
        Booking booking = bookingOf(maria, ALREADY_ENDED);
        jdbc.update("update bookings set status = 'CANCELLED_BY_STUDENT' where id = ?", booking.getId());

        ResponseEntity<Map> response = post(
                attendanceUrl(booking), mariaSession, new RecordAttendanceRequest(true, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aProfessorCannotRecordAttendanceOfAnotherProfessorsClass() {
        Booking juansClass = bookingOf(juan, ALREADY_ENDED);

        ResponseEntity<Map> response = post(
                attendanceUrl(juansClass), mariaSession, new RecordAttendanceRequest(true, null), Map.class);

        // 404, no 403: no le confirmamos a María que esa clase exista.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bookings.findById(juansClass.getId()).orElseThrow().isConfirmed()).isTrue();
    }

    @Test
    void aStudentCannotRecordAttendance() {
        Booking booking = bookingOf(maria, ALREADY_ENDED);

        ResponseEntity<Map> response = post(
                attendanceUrl(booking), anaSession, new RecordAttendanceRequest(true, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void recordingAttendanceOfAnUnknownBookingIsNotFound() {
        ResponseEntity<Map> response = post(
                "/api/v1/bookings/" + UUID.randomUUID() + "/attendance", mariaSession,
                new RecordAttendanceRequest(true, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theRecordedClassShowsUpAsPastWithItsFinalStatus() {
        Booking booking = bookingOf(maria, ALREADY_ENDED);
        post(attendanceUrl(booking), mariaSession, new RecordAttendanceRequest(true, null),
                AttendanceResponse.class);

        ResponseEntity<MyBookingResponse[]> past = get(
                "/api/v1/me/bookings?scope=past", mariaSession, MyBookingResponse[].class);

        assertThat(past.getBody()).hasSize(1);
        assertThat(past.getBody()[0].status()).isEqualTo("COMPLETED");
        assertThat(past.getBody()[0].canCancel()).isFalse();
    }
}
