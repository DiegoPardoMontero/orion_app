package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

import co.orion.scheduling.TestBookings;
import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/** Reloj congelado en el lunes 2026-07-13 a las 12:00 de Bogotá (= 17:00 UTC). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, CancelBookingIT.FrozenClockConfiguration.class})
class CancelBookingIT extends ApiIntegrationSupport {

    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15);
    /** Dentro de 5 horas: quedan menos de 24 h, ya no se puede cancelar. */
    private static final Instant SOON = FROZEN_NOW.plus(Duration.ofHours(5));

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

    private User ana;
    private User carlos;
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

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        carlos = createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        ProfessorProfile published = new ProfessorProfile(maria);
        published.changeRate(60_000L);   // sin tarifa no hay precio que cobrar y no se puede reservar
        published.publish();
        profiles.save(published);
        approveTeacher(maria.getId());
        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
        adminSession = login("admin@orion.test");
    }

    /** Miércoles 09:00 Bogotá: faltan casi 2 días, así que es cancelable. */
    private Instant wednesdayAt(int hour) {
        return ZonedDateTime.of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toInstant();
    }

    private Booking bookingAt(Instant startsAt) {
        return bookings.save(TestBookings.confirmed(ana.getId(), maria.getId(), startsAt,
                BookingModality.VIRTUAL, null, ana.getId()));
    }

    private String cancelUrl(Booking booking) {
        return "/api/v1/bookings/" + booking.getId() + "/cancel";
    }

    @Test
    void theStudentCancelsTheirOwnClass() {
        Booking booking = bookingAt(wednesdayAt(9));

        ResponseEntity<BookingResponse> response = post(
                cancelUrl(booking), anaSession, new CancelBookingRequest("Me salió un viaje"),
                BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("CANCELLED_BY_STUDENT");

        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getCancelledBy()).isEqualTo(ana.getId());
        assertThat(saved.getCancelledAt()).isEqualTo(FROZEN_NOW);
        assertThat(saved.getCancellationReason()).isEqualTo("Me salió un viaje");
    }

    @Test
    void theProfessorCancelsAndTheStatusSaysSo() {
        Booking booking = bookingAt(wednesdayAt(9));

        ResponseEntity<BookingResponse> response = post(
                cancelUrl(booking), mariaSession, new CancelBookingRequest(null), BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("CANCELLED_BY_PROFESSOR");
    }

    @Test
    void cancellingFreesTheSlotAgain() {
        Booking booking = bookingAt(wednesdayAt(9));

        ResponseEntity<SlotsResponse> before = get(
                "/api/v1/professors/" + maria.getId() + "/slots?from=2026-07-15&to=2026-07-15",
                anaSession, SlotsResponse.class);
        assertThat(before.getBody().slots()).hasSize(2);

        post(cancelUrl(booking), anaSession, new CancelBookingRequest(null), BookingResponse.class);

        ResponseEntity<SlotsResponse> after = get(
                "/api/v1/professors/" + maria.getId() + "/slots?from=2026-07-15&to=2026-07-15",
                anaSession, SlotsResponse.class);
        // El cupo vuelve a estar disponible: el índice único parcial lo permite y el cálculo lo ofrece.
        assertThat(after.getBody().slots()).hasSize(3);
    }

    /** La ventana son 12 h para los dos (decisión Q3), y se lee de platform_settings. */
    @Test
    void aStudentCannotCancelInsideTheCancellationWindow() {
        Booking booking = bookingAt(SOON);

        ResponseEntity<Map> response = post(
                cancelUrl(booking), anaSession, new CancelBookingRequest(null), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString())
                .isEqualTo("Faltan menos de 12 horas — la clase se considera impartida (política Orión)");
        assertThat(bookings.findById(booking.getId()).orElseThrow().isConfirmed()).isTrue();
    }

    /**
     * El profesor tampoco cancela dentro de la ventana, pero a él no se le deja sin salida: el
     * mensaje le ofrece proponer otro horario, que es la vía que sí tiene abierta.
     */
    @Test
    void aProfessorInsideTheWindowIsOfferedRescheduling() {
        Booking booking = bookingAt(SOON);

        ResponseEntity<Map> response = post(
                cancelUrl(booking), mariaSession, new CancelBookingRequest(null), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("proponerle");
    }

    @Test
    void anAdminCanCancelAtAnyTimeAsTheForceMajeureValve() {
        Booking booking = bookingAt(SOON);

        ResponseEntity<BookingResponse> response = post(
                cancelUrl(booking), adminSession, new CancelBookingRequest("Fuerza mayor"),
                BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("CANCELLED_BY_ADMIN");
    }

    @Test
    void cancellingTwiceIsAConflict() {
        Booking booking = bookingAt(wednesdayAt(9));
        post(cancelUrl(booking), anaSession, new CancelBookingRequest(null), BookingResponse.class);

        ResponseEntity<Map> response = post(
                cancelUrl(booking), anaSession, new CancelBookingRequest(null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancellingSomeoneElsesBookingIsNotFound() {
        Booking booking = bookingAt(wednesdayAt(9));
        Session carlosSession = login("carlos@orion.test");

        ResponseEntity<Map> response = post(
                cancelUrl(booking), carlosSession, new CancelBookingRequest(null), Map.class);

        // 404 y no 403: a Carlos no le confirmamos que esa reserva exista.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bookings.findById(booking.getId()).orElseThrow().isConfirmed()).isTrue();
    }

    @Test
    void cancellingABookingThatDoesNotExistIsNotFound() {
        ResponseEntity<Map> response = post(
                "/api/v1/bookings/" + UUID.randomUUID() + "/cancel", anaSession,
                new CancelBookingRequest(null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aCancelledSlotCanBeBookedAgain() {
        Booking booking = bookingAt(wednesdayAt(9));
        post(cancelUrl(booking), anaSession, new CancelBookingRequest(null), BookingResponse.class);

        Session carlosSession = login("carlos@orion.test");
        ResponseEntity<BookingResponse> response = post(
                "/api/v1/bookings", carlosSession,
                new CreateBookingRequest(maria.getId(),
                        wednesdayAt(9).atZone(BusinessZone.BOGOTA).toOffsetDateTime(),
                        "VIRTUAL", null, null),
                BookingResponse.class);

        // El índice único es parcial: la fila cancelada no bloquea la nueva reserva.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().studentId()).isEqualTo(carlos.getId());
    }
}
