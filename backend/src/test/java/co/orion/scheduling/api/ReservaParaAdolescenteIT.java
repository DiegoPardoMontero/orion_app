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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import co.orion.identity.api.AdolescentProfileResponse;
import co.orion.identity.domain.DocumentType;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.TeacherDocument;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherDocumentRepository;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.time.BusinessZone;
import co.orion.support.ApiIntegrationSupport;

/**
 * Las puertas del servidor para la clase de un adolescente (Bloque 12): solo su representante legal
 * reserva, solo con un profe habilitado, el aula del adulto espera su confirmación, y nada del menor
 * pasa por la IA del acta. Reloj congelado en el lunes 2026-07-13 a las 12:00 de Bogotá.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, ReservaParaAdolescenteIT.FrozenClockConfiguration.class})
class ReservaParaAdolescenteIT extends ApiIntegrationSupport {

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

    @Autowired private BookingRepository bookings;
    @Autowired private AvailabilityRuleRepository rules;
    @Autowired private ProfessorProfileRepository profiles;
    @Autowired private TeacherDocumentRepository documents;
    @Autowired private JdbcTemplate jdbc;

    private User maria;
    private User juan;
    private Session ana;
    private Session sesionMaria;
    private Session admin;
    private UUID sofi;

    @BeforeEach
    void seed() {
        // El reloj está congelado en julio y los textos de adolescentes rigen desde octubre: aquí se
        // adelantan para que la autorización y el código tengan versión vigente.
        jdbc.update("update legal_documents set effective_from = date '2026-01-01' where code in ('GUARDIAN_AUTHORIZATION', 'MINORS_CODE_OF_CONDUCT')");
        bookings.deleteAll();
        jdbc.update("delete from guardian_authorizations");
        jdbc.update("delete from adolescent_profiles");
        jdbc.update("delete from teacher_documents");
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        for (User u : List.of(maria, juan)) {
            ProfessorProfile p = new ProfessorProfile(u);
            p.changeRate(0L); // gratis: la reserva se confirma sola, sin pasarela
            p.publish();
            profiles.save(p);
            approveTeacher(u.getId());
            rules.save(new AvailabilityRule(u.getId(), DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(11, 0)));
        }
        ana = login("ana@orion.test");
        sesionMaria = login("maria@orion.test");
        admin = login("admin@orion.test");

        // María queda habilitada para adolescentes; Juan no.
        post("/api/v1/me/profile/minors/conduct", sesionMaria, null, Map.class);
        documents.save(new TeacherDocument(maria.getId(), null, DocumentType.REGISTRY_CHECK, "r.pdf", "k1", "application/pdf", 10));
        documents.save(new TeacherDocument(maria.getId(), null, DocumentType.BACKGROUND_CHECK, "a.pdf", "k2", "application/pdf", 10));
        post("/api/v1/admin/professors/" + maria.getId() + "/minors", admin, null, Map.class);

        // Sofi, 15 años, bajo la cuenta de Ana.
        sofi = post("/api/v1/me/adolescents", ana, Map.of("firstName", "Sofi", "birthYear", 2011,
                "selfDeclaredLevel", "BEGINNER", "motivation", "Inglés para el colegio",
                "acceptsAuthorization", true, "heardConfirmed", true), AdolescentProfileResponse.class).getBody().id();
    }

    private OffsetDateTime wednesdayAt(int hour) {
        return ZonedDateTime.of(WEDNESDAY, LocalTime.of(hour, 0), BusinessZone.BOGOTA).toOffsetDateTime();
    }

    private Map<String, Object> reserva(UUID profe, int hora, UUID perfil) {
        return Map.of("professorId", profe, "startsAt", wednesdayAt(hora).toString(), "modality", "VIRTUAL",
                "adolescentProfileId", perfil);
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("Solo con un profe habilitado; la reserva queda a nombre del adulto y dice para quién es")
    void reservaConProfeHabilitado() {
        ResponseEntity<Map> conJuan = post("/api/v1/bookings", ana, reserva(juan.getId(), 9, sofi), Map.class);
        assertThat(conJuan.getStatusCode().value()).isEqualTo(422);
        assertThat(conJuan.getBody().get("error").toString()).contains("habilitado");

        ResponseEntity<BookingResponse> conMaria = post("/api/v1/bookings", ana, reserva(maria.getId(), 9, sofi), BookingResponse.class);
        assertThat(conMaria.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var guardada = bookings.findById(conMaria.getBody().id()).orElseThrow();
        assertThat(guardada.getStudentId()).isEqualTo(users.findByEmailIgnoreCase("ana@orion.test").orElseThrow().getId());
        assertThat(guardada.getAdolescentProfileId()).isEqualTo(sofi);

        ResponseEntity<MyBookingResponse[]> mias = get("/api/v1/me/bookings?scope=upcoming", ana, MyBookingResponse[].class);
        assertThat(mias.getBody()).singleElement().satisfies(b -> assertThat(b.adolescentFirstName()).isEqualTo("Sofi"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("El perfil de otro adulto, o revocado, no sirve para reservar")
    void soloElSuyoYVivo() {
        createUser("carlos@orion.test", "Carlos Díaz", UserRole.STUDENT);
        assertThat(post("/api/v1/bookings", login("carlos@orion.test"), reserva(maria.getId(), 9, sofi), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        delete("/api/v1/me/adolescents/" + sofi, ana, Void.class);
        assertThat(post("/api/v1/bookings", ana, reserva(maria.getId(), 9, sofi), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("El aula del adulto espera su «estoy presente o cerca»; la del profe no")
    void elAulaEsperaAlAdulto() {
        UUID id = post("/api/v1/bookings", ana, reserva(maria.getId(), 9, sofi), BookingResponse.class).getBody().id();
        // Movemos la clase a dentro de cinco minutos para que la antesala esté abierta.
        jdbc.update("update bookings set starts_at = ?, ends_at = ? where id = ?",
                java.sql.Timestamp.from(FROZEN_NOW.plusSeconds(300)), java.sql.Timestamp.from(FROZEN_NOW.plusSeconds(300 + 55 * 60)), id);

        ClassroomResponse antes = get("/api/v1/bookings/" + id + "/classroom", ana, ClassroomResponse.class).getBody();
        assertThat(antes.guardianConfirmationRequired()).isTrue();
        assertThat(antes.token()).isNull();
        assertThat(antes.adolescentFirstName()).isEqualTo("Sofi");
        assertThat(get("/api/v1/bookings/" + id + "/classroom", sesionMaria, ClassroomResponse.class).getBody()
                .guardianConfirmationRequired()).isFalse();

        ClassroomResponse despues = post("/api/v1/bookings/" + id + "/classroom/guardian-present", ana, null, ClassroomResponse.class).getBody();
        assertThat(despues.guardianConfirmationRequired()).isFalse();
        assertThat(bookings.findById(id).orElseThrow().getGuardianPresentConfirmedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("El acta de una clase de adolescente no pasa por la IA")
    void sinIaEnElActa() {
        UUID id = post("/api/v1/bookings", ana, reserva(maria.getId(), 9, sofi), BookingResponse.class).getBody().id();
        jdbc.update("update bookings set status = 'COMPLETED', completed_at = now(), starts_at = now() - interval '2 hours', ends_at = now() - interval '1 hour' where id = ?", id);

        ResponseEntity<Map> borrador = post("/api/v1/bookings/" + id + "/lesson-note/draft", sesionMaria,
                Map.of("rawInput", "Trabajamos los colores y los días de la semana."), Map.class);
        assertThat(borrador.getStatusCode().value()).isEqualTo(422);
        assertThat(borrador.getBody().get("error").toString()).contains("adolescentes");
    }
}
