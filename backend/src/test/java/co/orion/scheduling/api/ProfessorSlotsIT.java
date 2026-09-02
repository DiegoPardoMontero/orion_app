package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
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
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.domain.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityExceptionRepository;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El reloj está congelado en el lunes 2026-07-13 a las 12:00 de Bogotá, así que "hoy", el rango
 * por defecto y el filtro de pasado son deterministas. Aquí es donde el bean Clock de la Tarea 1
 * paga su deuda: sin él habría que esperar a que el calendario colaborara.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, ProfessorSlotsIT.FrozenClockConfiguration.class})
class ProfessorSlotsIT extends ApiIntegrationSupport {

    /** Lunes 2026-07-13, 12:00 en Bogotá (= 17:00 UTC). */
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
    private AvailabilityRuleRepository rules;

    @Autowired
    private AvailabilityExceptionRepository exceptions;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private JdbcTemplate jdbc;

    private User maria;
    private User juan;
    private Session mariaSession;
    private Session anaSession;

    @BeforeEach
    void seed() {
        // bookings primero: sus FK a users no tienen cascada (son registros de negocio).
        bookings.deleteAll();
        exceptions.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);

        ProfessorProfile mariaProfile = new ProfessorProfile(maria);
        mariaProfile.publish();
        profiles.save(mariaProfile);
        approveTeacher(maria.getId());
        profiles.save(new ProfessorProfile(juan)); // sin publicar

        mariaSession = login("maria@orion.test");
        anaSession = login("ana@orion.test");

        // Juan tiene reglas, pero no está publicado: no debe exponer cupos.
        rules.save(new AvailabilityRule(juan.getId(), DayOfWeek.TUESDAY, LocalTime.of(15, 0), LocalTime.of(18, 0)));
    }

    private String slotsUrl(User professor, String query) {
        return "/api/v1/professors/" + professor.getId() + "/slots" + query;
    }

    @Test
    void aPartialExceptionRemovesExactlyOneSlotOfTheDay() {
        post("/api/v1/me/availability/rules", mariaSession,
                new CreateRuleRequest(3, LocalTime.of(8, 0), LocalTime.of(11, 0)), RuleResponse.class);
        post("/api/v1/me/availability/exceptions", mariaSession,
                new CreateExceptionRequest(WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "Cita"),
                ExceptionResponse.class);

        ResponseEntity<String> response = get(
                slotsUrl(maria, "?from=2026-07-15&to=2026-07-15"), anaSession, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Exactamente 08:00 y 10:00, con el offset de Bogotá. El de las 09:00 lo mató la excepción.
        assertThat(response.getBody()).contains("\"America/Bogota\"");
        assertThat(response.getBody()).contains("\"startsAt\":\"2026-07-15T08:00:00-05:00\"");
        assertThat(response.getBody()).contains("\"startsAt\":\"2026-07-15T10:00:00-05:00\"");
        // Ojo: hay que anclar el "startsAt". Las 09:00 sí aparecen en el JSON, como endsAt del
        // cupo de las 08:00 — lo que no puede existir es un cupo que EMPIECE a las 09:00.
        assertThat(response.getBody()).doesNotContain("\"startsAt\":\"2026-07-15T09:00:00");

        SlotsResponse parsed = get(
                slotsUrl(maria, "?from=2026-07-15&to=2026-07-15"), anaSession, SlotsResponse.class).getBody();
        assertThat(parsed.slots()).hasSize(2);
        assertThat(parsed.professorId()).isEqualTo(maria.getId());
    }

    @Test
    void withoutParametersTheRangeIsTodayPlusSixDays() {
        post("/api/v1/me/availability/rules", mariaSession,
                new CreateRuleRequest(3, LocalTime.of(8, 0), LocalTime.of(11, 0)), RuleResponse.class);

        ResponseEntity<SlotsResponse> response = get(slotsUrl(maria, ""), anaSession, SlotsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // El rango por defecto (lun 13 → dom 19) contiene un solo miércoles: 3 cupos.
        assertThat(response.getBody().slots()).hasSize(3);
        assertThat(response.getBody().slots())
                .allSatisfy(slot -> assertThat(slot.startsAt().toLocalDate()).isEqualTo(WEDNESDAY));
    }

    @Test
    void slotsOfAnUnpublishedProfessorAreNotFoundEvenWhenHeHasRules() {
        ResponseEntity<Map> response = get(slotsUrl(juan, ""), anaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aRangeLongerThanThirtyOneDaysIsRejected() {
        ResponseEntity<Map> response = get(
                slotsUrl(maria, "?from=2026-07-15&to=2026-09-15"), anaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("31");
    }

    @Test
    void aFromAfterTheToIsRejected() {
        ResponseEntity<Map> response = get(
                slotsUrl(maria, "?from=2026-07-20&to=2026-07-15"), anaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("posterior");
    }

    @Test
    void slotsThatAlreadyStartedTodayAreNotOffered() {
        // El reloj está en el lunes a las 12:00: una regla del lunes 08:00–15:00 solo debe
        // ofrecer 13:00 y 14:00. Las de la mañana ya pasaron y la de las 12:00 está en curso.
        post("/api/v1/me/availability/rules", mariaSession,
                new CreateRuleRequest(1, LocalTime.of(8, 0), LocalTime.of(15, 0)), RuleResponse.class);

        ResponseEntity<SlotsResponse> response = get(
                slotsUrl(maria, "?from=2026-07-13&to=2026-07-13"), anaSession, SlotsResponse.class);

        assertThat(response.getBody().slots()).hasSize(2);
        // Comparamos instantes, no la hora de pared: el cliente HTTP del test deserializa el
        // ZonedDateTime pasándolo a UTC, aunque el JSON del servidor viene en -05:00.
        assertThat(response.getBody().slots().getFirst().startsAt().toInstant())
                .isEqualTo(ZonedDateTime.of(
                        LocalDate.of(2026, 7, 13), LocalTime.of(13, 0), BusinessZone.BOGOTA).toInstant());
    }

    @Test
    void aConfirmedBookingRemovesExactlyItsSlot() {
        post("/api/v1/me/availability/rules", mariaSession,
                new CreateRuleRequest(3, LocalTime.of(8, 0), LocalTime.of(11, 0)), RuleResponse.class);
        UUID ana = users.findByEmailIgnoreCase("ana@orion.test").orElseThrow().getId();

        // El cupo de las 09:00 del miércoles queda reservado.
        bookings.save(TestBookings.confirmed(ana, maria.getId(),
                wednesdayAt(9), wednesdayAt(10),
                BookingModality.VIRTUAL, null, ana));

        ResponseEntity<SlotsResponse> response = get(
                slotsUrl(maria, "?from=2026-07-15&to=2026-07-15"), anaSession, SlotsResponse.class);

        assertThat(response.getBody().slots()).hasSize(2);
        assertThat(response.getBody().slots())
                .noneMatch(slot -> slot.startsAt().toInstant().equals(wednesdayAt(9)));
    }

    @Test
    void aCancelledBookingDoesNotRemoveItsSlot() {
        post("/api/v1/me/availability/rules", mariaSession,
                new CreateRuleRequest(3, LocalTime.of(8, 0), LocalTime.of(11, 0)), RuleResponse.class);
        UUID ana = users.findByEmailIgnoreCase("ana@orion.test").orElseThrow().getId();

        Booking booking = bookings.save(TestBookings.confirmed(ana, maria.getId(),
                wednesdayAt(9), wednesdayAt(10),
                BookingModality.VIRTUAL, null, ana));
        // Cancelarla libera el cupo. La operación de cancelar llega en el Paso 4; aquí basta
        // con dejar la fila en un estado no CONFIRMED, que es lo único que mira el cálculo.
        jdbc.update("update bookings set status = 'CANCELLED_BY_STUDENT' where id = ?", booking.getId());

        ResponseEntity<SlotsResponse> response = get(
                slotsUrl(maria, "?from=2026-07-15&to=2026-07-15"), anaSession, SlotsResponse.class);

        assertThat(response.getBody().slots()).hasSize(3);
        assertThat(response.getBody().slots())
                .anyMatch(slot -> slot.startsAt().toInstant().equals(wednesdayAt(9)));
    }

    private Instant wednesdayAt(int hour) {
        return ZonedDateTime.of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toInstant();
    }

    @Test
    void anAnonymousUserCannotQuerySlots() {
        ResponseEntity<Map> response = rest.getForEntity(slotsUrl(maria, ""), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
