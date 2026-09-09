package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.StudentProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/**
 * La cuenta anterior al Bloque 9 que declara su mayoría de edad sin volver a entrar.
 *
 * <p>El aviso que se le muestra al entrar solo desaparece cuando {@code /auth/me} dice que la
 * declaración está dada. Ese endpoint responde con el principal de la sesión, no con la fila de la
 * base: si nadie lo refresca, la persona confirma, el 204 llega, y el diálogo sigue ahí — sin
 * error que enseñar, porque no hubo ninguno.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class AdulthoodConfirmationIT extends ApiIntegrationSupport {

    private static final String LEGACY_EMAIL = "antigua@orion.test";

    @BeforeEach
    void seedLegacyAccount() {
        users.deleteAll();

        // Sin confirmAdulthood(): exactamente como la dejó la V24, que no fabricó constancias.
        User antigua = new User(LEGACY_EMAIL, passwordEncoder.encode(PASSWORD), "Ana Antigua", UserRole.STUDENT);
        antigua.markEmailVerified(Instant.now());
        studentProfiles.save(new StudentProfile(users.save(antigua)));
    }

    @Test
    void confirmingAdulthoodIsVisibleInTheSameSession() {
        Session sesion = login(LEGACY_EMAIL);

        assertThat(get("/api/v1/auth/me", sesion, UserResponse.class).getBody().adultConfirmed())
                .isFalse();

        ResponseEntity<Void> confirmacion =
                post("/api/v1/me/account/adulthood", sesion, null, Void.class);
        assertThat(confirmacion.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // La misma sesión, sin volver a entrar: es lo único que hace desaparecer el diálogo.
        assertThat(get("/api/v1/auth/me", sesion, UserResponse.class).getBody().adultConfirmed())
                .isTrue();
    }
}
