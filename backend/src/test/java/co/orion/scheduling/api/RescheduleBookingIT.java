package co.orion.scheduling.api;

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
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/** Reloj congelado el lunes 2026-07-13 a las 12:00 de Bogotá (idéntico a CreateBookingIT). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, RescheduleBookingIT.FrozenClockConfiguration.class})
class RescheduleBookingIT extends ApiIntegrationSupport {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final Instant FROZEN_NOW = Instant.parse("2026-07-13T17:00:00Z");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 7, 15); // > 24 h
    private static final LocalDate TUESDAY = LocalDate.of(2026, 7, 14); // < 24 h

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
    private User maria;
    private Session anaSession;
    private Session adminSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        ProfessorProfile published = new ProfessorProfile(maria);
        published.changeRate(60_000L);
        published.publish();
        profiles.save(published);
        approveTeacher(maria.getId());

        // Miércoles (lejano) y martes (dentro de 24 h): cupos 8, 9, 10 cada día.
        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(11, 0)));
        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");
        adminSession = login("admin@orion.test");
    }

    private OffsetDateTime at(LocalDate day, int hour) {
        return ZonedDateTime.of(day, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
    }

    private UUID book(Session session, LocalDate day, int hour) {
        ResponseEntity<BookingResponse> response = post(
                BOOKINGS, session,
                new CreateBookingRequest(maria.getId(), at(day, hour), "VIRTUAL", null, null),
                BookingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Reprogramar es una operación sobre una clase que existe, y una clase existe cuando está
        // pagada: la reserva se paga aquí para que el test hable del reagendamiento y no del cobro.
        approvePayment(response.getBody().id());
        return response.getBody().id();
    }

    private <T> ResponseEntity<T> reschedule(Session session, UUID id, LocalDate day, int hour, Class<T> type) {
        return post(BOOKINGS + "/" + id + "/reschedule", session,
                new RescheduleBookingRequest(at(day, hour)), type);
    }

    @Test
    void aStudentReschedulesToAnotherFreeSlot() {
        UUID id = book(anaSession, WEDNESDAY, 9);

        ResponseEntity<BookingResponse> response = reschedule(anaSession, id, WEDNESDAY, 10, BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(id); // misma reserva, nuevo horario
        assertThat(response.getBody().startsAt().toInstant()).isEqualTo(at(WEDNESDAY, 10).toInstant());
        assertThat(response.getBody().endsAt().toInstant()).isEqualTo(at(WEDNESDAY, 11).toInstant());

        // El cupo viejo (9) vuelve a estar libre; el nuevo (10) queda tomado → siguen 2 libres (8 y 9).
        ResponseEntity<SlotsResponse> slots = get(
                "/api/v1/professors/" + maria.getId() + "/slots?from=2026-07-15&to=2026-07-15",
                anaSession, SlotsResponse.class);
        assertThat(slots.getBody().slots()).hasSize(2);
    }

    @Test
    void reschedulingToTheSameTimeIsRejected() {
        UUID id = book(anaSession, WEDNESDAY, 9);

        ResponseEntity<Map> response = reschedule(anaSession, id, WEDNESDAY, 9, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("distinto");
    }

    @Test
    void reschedulingToAnOccupiedSlotIsUnprocessable() {
        UUID anaBooking = book(anaSession, WEDNESDAY, 9);
        book(login("carlos@orion.test"), WEDNESDAY, 10);

        ResponseEntity<Map> response = reschedule(anaSession, anaBooking, WEDNESDAY, 10, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void aStudentCannotRescheduleWithin24Hours() {
        UUID id = book(anaSession, TUESDAY, 9); // martes: < 24 h del reloj congelado

        ResponseEntity<Map> response = reschedule(anaSession, id, TUESDAY, 10, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("24 horas");
    }

    @Test
    void anAdminCanRescheduleEvenWithin24Hours() {
        UUID id = book(anaSession, TUESDAY, 9);

        ResponseEntity<BookingResponse> response = reschedule(adminSession, id, TUESDAY, 10, BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().startsAt().toInstant()).isEqualTo(at(TUESDAY, 10).toInstant());
    }

    @Test
    void reschedulingSomeoneElsesBookingIsNotFound() {
        UUID anaBooking = book(anaSession, WEDNESDAY, 9);

        ResponseEntity<Map> response = reschedule(login("carlos@orion.test"), anaBooking, WEDNESDAY, 10, Map.class);

        // Ajena → 404, no confirmamos que exista.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aProfessorCannotReschedule() {
        UUID anaBooking = book(anaSession, WEDNESDAY, 9);

        ResponseEntity<Map> response = reschedule(login("maria@orion.test"), anaBooking, WEDNESDAY, 10, Map.class);

        // Bloqueado en la capa de seguridad: reprogramar no es acción del profesor.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
