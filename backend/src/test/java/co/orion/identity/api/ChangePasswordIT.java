package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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

    @Test
    void aWrongCurrentPasswordIsRejectedAndChangesNothing() {
        ResponseEntity<Map> response = post(
                PASSWORD_URL, anaSession, new ChangePasswordRequest("no-es-la-mia", NUEVA), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().get("error").toString()).contains("actual no es correcta");
        // La contraseña original sigue funcionando: no se tocó nada.
        assertThat(login("ana@orion.test", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
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
