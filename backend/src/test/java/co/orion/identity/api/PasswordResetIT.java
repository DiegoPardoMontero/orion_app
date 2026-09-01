package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

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
import co.orion.identity.application.PasswordResetMailer;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.PasswordResetTokenRepository;
import co.orion.support.ApiIntegrationSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, PasswordResetIT.CapturingMailerConfig.class})
class PasswordResetIT extends ApiIntegrationSupport {

    private static final String EMAIL = "ana@orion.test";
    private static final String NEW_PASSWORD = "clave-nueva-1";

    /** Sustituye al mailer SMTP: captura el enlace para poder extraer el token (que solo va al correo). */
    @TestConfiguration
    static class CapturingMailerConfig {
        @Bean
        @Primary
        CapturingMailer capturingMailer() {
            return new CapturingMailer();
        }
    }

    static class CapturingMailer implements PasswordResetMailer {
        volatile String lastLink;
        volatile String lastEmail;

        @Override
        public void sendResetLink(String toEmail, String fullName, String resetLink) {
            this.lastEmail = toEmail;
            this.lastLink = resetLink;
        }
    }

    @Autowired
    private CapturingMailer mailer;
    @Autowired
    private PasswordResetTokenRepository tokens;

    @BeforeEach
    void seed() {
        tokens.deleteAll();
        users.deleteAll();
        createUser(EMAIL, "Ana Ramírez", UserRole.STUDENT);
        mailer.lastLink = null;
        mailer.lastEmail = null;
    }

    private ResponseEntity<Void> forgot(String email) {
        return rest.postForEntity("/api/v1/auth/forgot-password", new ForgotPasswordRequest(email), Void.class);
    }

    private <T> ResponseEntity<T> reset(String token, String newPassword, Class<T> type) {
        return rest.postForEntity(
                "/api/v1/auth/reset-password", new ResetPasswordRequest(token, newPassword), type);
    }

    private String tokenFromLastLink() {
        return mailer.lastLink.substring(mailer.lastLink.indexOf("token=") + "token=".length());
    }

    private int loginStatus(String email, String password) {
        return rest.postForEntity("/api/v1/auth/login", new LoginRequest(email, password), Void.class)
                .getStatusCode().value();
    }

    @Test
    void requestingForAnExistingEmailSendsALink() {
        assertThat(forgot(EMAIL).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(mailer.lastEmail).isEqualTo(EMAIL);
        assertThat(mailer.lastLink).contains("/restablecer?token=");
        assertThat(tokens.count()).isEqualTo(1);
    }

    @Test
    void requestingForAnUnknownEmailRevealsNothing() {
        // Mismo 204 que un correo real, pero ni token ni correo: no hay oráculo de correos.
        assertThat(forgot("nadie@orion.test").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(mailer.lastLink).isNull();
        assertThat(tokens.count()).isZero();
    }

    @Test
    void resettingWithAValidTokenChangesThePassword() {
        forgot(EMAIL);
        String token = tokenFromLastLink();

        assertThat(reset(token, NEW_PASSWORD, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(loginStatus(EMAIL, NEW_PASSWORD)).isEqualTo(200);
        assertThat(loginStatus(EMAIL, PASSWORD)).isEqualTo(401); // la vieja ya no sirve
    }

    @Test
    void aTokenIsSingleUse() {
        forgot(EMAIL);
        String token = tokenFromLastLink();

        assertThat(reset(token, NEW_PASSWORD, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // El mismo enlace no vuelve a servir.
        assertThat(reset(token, "otra-clave-2", Map.class).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void requestingAgainInvalidatesThePreviousToken() {
        forgot(EMAIL);
        String primero = tokenFromLastLink();
        forgot(EMAIL);

        // El primer enlace ya no vale; solo el último.
        assertThat(reset(primero, NEW_PASSWORD, Map.class).getStatusCode().value()).isEqualTo(422);
        assertThat(reset(tokenFromLastLink(), NEW_PASSWORD, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void resettingWithAnInvalidTokenIsUnprocessable() {
        assertThat(reset("token-inventado", NEW_PASSWORD, Map.class).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void resettingWithAShortPasswordIsRejected() {
        forgot(EMAIL);
        String token = tokenFromLastLink();

        assertThat(reset(token, "corta", Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
