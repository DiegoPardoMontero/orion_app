package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.support.ApiIntegrationSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class AdminUsersIT extends ApiIntegrationSupport {

    private static final String USERS = "/api/v1/admin/users";

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private TeacherApplicationRepository applications;

    private User ana;
    private Session adminSession;
    private Session anaSession;

    @BeforeEach
    void seed() {
        profiles.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        adminSession = login("admin@orion.test");
        anaSession = login("ana@orion.test");
    }

    @Test
    void theAdminListsEveryUser() {
        ResponseEntity<AdminUserResponse[]> response = get(USERS, adminSession, AdminUserResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(3);
    }

    @Test
    void filtersByRole() {
        ResponseEntity<AdminUserResponse[]> response = get(
                USERS + "?role=PROFESSOR", adminSession, AdminUserResponse[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].fullName()).isEqualTo("María Gómez");
    }

    @Test
    void searchesByNameOrEmail() {
        assertThat(get(USERS + "?q=ramírez", adminSession, AdminUserResponse[].class).getBody()).hasSize(1);
        assertThat(get(USERS + "?q=ORION.TEST", adminSession, AdminUserResponse[].class).getBody()).hasSize(3);
        assertThat(get(USERS + "?q=nadie", adminSession, AdminUserResponse[].class).getBody()).isEmpty();
    }

    @Test
    void createsAStudent() {
        ResponseEntity<AdminUserResponse> response = post(
                USERS, adminSession,
                new CreateUserRequest("nuevo@orion.test", "Nuevo Estudiante", "+573001112233",
                        "STUDENT", "clave-larga-1"),
                AdminUserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().role()).isEqualTo("STUDENT");
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");
        // El correo se normaliza en el dominio, no en la base.
        assertThat(users.findByEmailIgnoreCase("NUEVO@orion.test")).isPresent();
    }

    @Test
    void aNewProfessorIsBornWithAnEmptyUnpublishedProfile() {
        ResponseEntity<AdminUserResponse> response = post(
                USERS, adminSession,
                new CreateUserRequest("profe@orion.test", "Profe Nuevo", null, "PROFESSOR", PASSWORD),
                AdminUserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Sin perfil no podría publicarse nunca: nace con uno, sin publicar.
        var perfil = profiles.findById(response.getBody().id());
        assertThat(perfil).isPresent();
        assertThat(perfil.get().isPublished()).isFalse();

        // Y aprobado por quien lo creó: sin una postulación APPROVED, publicar respondía 403 y el profe
        // dado de alta desde Usuarios no tenía salida (revisión del 28/09/2026).
        assertThat(applications.existsByUserIdAndStatus(response.getBody().id(), ApplicationStatus.APPROVED)).isTrue();
        Session profe = login("profe@orion.test");
        assertThat(put("/api/v1/me/profile/rate", profe, new RateRequest(50_000L), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        UpdateProfileRequest publicar = new UpdateProfileRequest(
                "Conversación en inglés para adultos", "Enseño inglés conversacional a adultos que ya estudiaron el idioma alguna vez y aun así no se atreven a hablarlo. Practicamos desde la primera clase con temas que te importan.", "CO", "Bogotá", "ES",
                (short) 5, "Lic. en Lenguas", false, false,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER"))),
                List.of("CONVERSATION"), true);
        assertThat(put("/api/v1/me/profile", profe, publicar, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aDuplicateEmailIsAConflict() {
        ResponseEntity<Map> response = post(
                USERS, adminSession,
                new CreateUserRequest("ana@orion.test", "Ana Duplicada", null, "STUDENT", "clave-larga-1"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error").toString()).contains("correo");
    }

    @Test
    void theAdminRoleCannotBeCreatedFromThePanel() {
        ResponseEntity<Map> response = post(
                USERS, adminSession,
                new CreateUserRequest("otro@orion.test", "Otro Admin", null, "ADMIN", "clave-larga-1"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aShortPasswordIsRejected() {
        ResponseEntity<Map> response = post(
                USERS, adminSession,
                new CreateUserRequest("corto@orion.test", "Clave Corta", null, "STUDENT", "corta"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deactivatingAUserStopsThemFromLoggingIn() {
        ResponseEntity<AdminUserResponse> response = patch(
                USERS + "/" + ana.getId(), adminSession,
                new UpdateUserRequest(null, null, "INACTIVE"),
                AdminUserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("INACTIVE");

        ResponseEntity<Map> intentoDeLogin = rest.postForEntity(
                "/api/v1/auth/login",
                Map.of("email", "ana@orion.test", "password", PASSWORD),
                Map.class);
        assertThat(intentoDeLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void updatesNameAndPhone() {
        ResponseEntity<AdminUserResponse> response = patch(
                USERS + "/" + ana.getId(), adminSession,
                new UpdateUserRequest("Ana María Ramírez", "+573009998877", null),
                AdminUserResponse.class);

        assertThat(response.getBody().fullName()).isEqualTo("Ana María Ramírez");
        assertThat(response.getBody().whatsappPhone()).isEqualTo("+573009998877");
        // Lo que no viene en el PATCH no se toca.
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");
    }

    @Test
    void patchingAnUnknownUserIsNotFound() {
        ResponseEntity<Map> response = patch(
                USERS + "/" + UUID.randomUUID(), adminSession,
                new UpdateUserRequest("Nadie", null, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aStudentCannotUseTheAdminEndpoints() {
        assertThat(get(USERS, anaSession, Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(USERS, anaSession,
                new CreateUserRequest("x@orion.test", "X", null, "STUDENT", "clave-larga-1"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
