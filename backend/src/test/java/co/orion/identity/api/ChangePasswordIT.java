package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ChangePasswordIT extends ApiIntegrationSupport {

    private static final String PASSWORD_URL = "/api/v1/me/password";
    private static final String NUEVA = "clave-nueva-1";

    private Session anaSession;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        users.deleteAll();
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        anaSession = login("ana@orion.test");
    }

    private ResponseEntity<Map> login(String email, String password) {
        return rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", password), Map.class);
    }

    @Test
    void changesThePasswordAndTheNewOneWorks() {
        ResponseEntity<Void> response = post(
                PASSWORD_URL, anaSession, new ChangePasswordRequest(PASSWORD, NUEVA), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login("ana@orion.test", NUEVA).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Y la vieja deja de servir.
        assertThat(login("ana@orion.test", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Quien cambia la contraseña porque sospecha de un intruso tiene que poder echarlo: la otra
     * sesión se cierra en su siguiente petición. La suya sigue, con un id de sesión nuevo.
     */
    @Test
    void changingThePasswordClosesTheOtherSessionsButNotTheOwn() {
        Session intruso = login("ana@orion.test");
        assertThat(get("/api/v1/auth/me", intruso, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> cambio = post(
                PASSWORD_URL, anaSession, new ChangePasswordRequest(PASSWORD, NUEVA), Void.class);
        assertThat(cambio.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(get("/api/v1/auth/me", intruso, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        String renovada = cookieValue(cambio, "ORION_SESSION");
        assertThat(renovada).isNotNull().isNotEqualTo(anaSession.cookie());
        assertThat(get("/api/v1/auth/me", new Session(renovada, anaSession.csrfToken()), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aWrongCurrentPasswordIsRejectedAndChangesNothing() {
        ResponseEntity<Map> response = post(
                PASSWORD_URL, anaSession, new ChangePasswordRequest("no-es-la-mia", NUEVA), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("actual no es correcta");
        // La contraseña original sigue funcionando: no se tocó nada.
        assertThat(login("ana@orion.test", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Quien entró con Google nació con una contraseña al azar: la primera la crea sin la actual, y
     * desde ahí es como cualquiera (24/09/2026).
     */
    @SuppressWarnings("rawtypes")
    @Test
    void anAccountWithoutAPasswordCreatesItsFirstOneWithoutTheCurrent() {
        jdbc.update("update users set password_set = false where email = 'ana@orion.test'");
        assertThat(get("/api/v1/auth/me", anaSession, Map.class).getBody()).containsEntry("hasPassword", false);

        ResponseEntity<Void> creada = post(PASSWORD_URL, anaSession, new ChangePasswordRequest(null, NUEVA), Void.class);

        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login("ana@orion.test", NUEVA).getStatusCode()).isEqualTo(HttpStatus.OK);
        Session nueva = new Session(cookieValue(creada, "ORION_SESSION"), anaSession.csrfToken());
        assertThat(get("/api/v1/auth/me", nueva, Map.class).getBody()).containsEntry("hasPassword", true);
        // La segunda vez ya pide la actual.
        assertThat(post(PASSWORD_URL, nueva, new ChangePasswordRequest(null, "otra-clave-2"), Map.class)
                .getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void aShortNewPasswordIsRejected() {
        ResponseEntity<Map> response = post(
                PASSWORD_URL, anaSession, new ChangePasswordRequest(PASSWORD, "corta"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anAnonymousUserCannotChangeAPassword() {
        ResponseEntity<Map> response = rest.postForEntity(
                PASSWORD_URL, new ChangePasswordRequest(PASSWORD, NUEVA), Map.class);

        // 403 (no 401): sin sesión tampoco hay token CSRF, y ese filtro corre antes que el de
        // autenticación. De todos modos, el anónimo no pasa: es lo único que importa aquí.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
