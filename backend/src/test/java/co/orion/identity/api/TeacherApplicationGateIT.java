package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
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
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.api.CreateBookingRequest;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.shared.time.BusinessZone;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El gate de visibilidad, por endpoint: un profesor publicado pero NO aprobado (PENDING_REVIEW) no
 * aparece en el directorio, no puede publicar, no expone cupos y no puede recibir reservas.
 * Reloj congelado en el lunes 2026-07-13 a las 12:00 de Bogotá (como en CreateBookingIT).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, TeacherApplicationGateIT.FrozenClockConfiguration.class})
class TeacherApplicationGateIT extends ApiIntegrationSupport {

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
    private ProfessorProfileRepository profiles;
    @Autowired
    private AvailabilityRuleRepository rules;
    @Autowired
    private BookingRepository bookings;

    private User pending;
    private Session anaSession;
    private Session pendingSession;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();

        pending = createUser("pending@orion.test", "Pen Diente", UserRole.PROFESSOR);
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);

        // Publicado y con tarifa (bypass del gate #3 vía repo), pero SIN aprobación: solo PENDING_REVIEW.
        ProfessorProfile profile = new ProfessorProfile(pending);
        profile.changeRate(50000L);
        profile.publish();
        profiles.save(profile);
        teacherApplications.saveAndFlush(
                new TeacherApplication(pending.getId(), ApplicationStatus.PENDING_REVIEW, null, null));

        rules.save(new AvailabilityRule(pending.getId(), DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(11, 0)));

        anaSession = login("ana@orion.test");
        pendingSession = login("pending@orion.test");
    }

    private OffsetDateTime wednesdayAt(int hour) {
        return ZonedDateTime.of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
    }

    @Test
    void aPendingProfessorDoesNotAppearInTheDirectory() {
        ResponseEntity<PagedProfessors> res = get("/api/v1/professors", anaSession, PagedProfessors.class);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().content()).isEmpty();
    }

    @Test
    void aPendingProfessorPublicDetailIsNotFound() {
        ResponseEntity<Map> res = get("/api/v1/professors/" + pending.getId(), anaSession, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void aPendingProfessorCannotPublish() {
        // La ficha es válida a propósito: lo que este test aísla es el gate de la postulación,
        // y con textos demasiado cortos el 422 taparía el 403 que se quiere comprobar.
        UpdateProfileRequest req = new UpdateProfileRequest(
                "Conversación en inglés para adultos", "Me encanta enseñar y llevo años acompañando a estudiantes que quieren soltarse al hablar. Cada clase se arma alrededor de lo que necesitas contar esa semana.",
                "CO", "Bogotá", "ES", (short) 3, "Educación", false, true,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER"))),
                List.of("CONVERSATION"), true);
        ResponseEntity<Map> res = put("/api/v1/me/profile", pendingSession, req, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void aPendingProfessorExposesNoSlots() {
        ResponseEntity<Map> res = get(
                "/api/v1/professors/" + pending.getId() + "/slots?from=2026-07-15&to=2026-07-15",
                anaSession, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void aPendingProfessorCannotReceiveABooking() {
        ResponseEntity<Map> res = post("/api/v1/bookings", anaSession,
                new CreateBookingRequest(pending.getId(), wednesdayAt(9), "VIRTUAL", null, null, null), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }
}
