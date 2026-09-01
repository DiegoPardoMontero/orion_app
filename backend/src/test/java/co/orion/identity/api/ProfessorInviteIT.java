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
import co.orion.identity.application.ProfessorInviteMailer;
import co.orion.identity.domain.UserRole;
import co.orion.identity.domain.UserStatus;
import co.orion.support.ApiIntegrationSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, ProfessorInviteIT.CapturingMailerConfig.class})
class ProfessorInviteIT extends ApiIntegrationSupport {

    private static final String INVITE = "/api/v1/admin/professors/invite";
    private static final String ACCEPT = "/api/v1/auth/accept-invite";
    private static final String NEW_PROFESSOR = "profe.nuevo@orion.test";

    @TestConfiguration
    static class CapturingMailerConfig {
        @Bean
        @Primary
        CapturingMailer capturingMailer() {
            return new CapturingMailer();
        }
    }

    static class CapturingMailer implements ProfessorInviteMailer {
        volatile String lastLink;

        @Override
        public void sendInvite(String toEmail, String inviteLink) {
            this.lastLink = inviteLink;
        }
    }

    @Autowired
    private CapturingMailer mailer;

    private Session adminSession;

    @BeforeEach
    void seed() {
        users.deleteAll();
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        adminSession = login("admin@orion.test");
        mailer.lastLink = null;
    }

    private String tokenFromLastLink() {
        return mailer.lastLink.substring(mailer.lastLink.indexOf("token=") + "token=".length());
    }

    private ResponseEntity<UserResponse> accept(String token, String fullName, String password) {
        return rest.postForEntity(ACCEPT,
                new AcceptInviteRequest(token, fullName, password, "+573001112233",
                        "Conversación · A1-B1", "Me encanta enseñar."),
                UserResponse.class);
    }

    @Test
    void invitingCreatesAPendingProfessorAndSendsALink() {
        ResponseEntity<Void> response = post(
                INVITE, adminSession, new InviteProfessorRequest(NEW_PROFESSOR), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(mailer.lastLink).contains("/invitacion?token=");

        var pending = users.findByEmailIgnoreCase(NEW_PROFESSOR).orElseThrow();
        assertThat(pending.getRole()).isEqualTo(UserRole.PROFESSOR);
        assertThat(pending.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void invitingAnExistingActiveEmailIsRejected() {
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);

        ResponseEntity<Map> response = post(
                INVITE, adminSession, new InviteProfessorRequest("ana@orion.test"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void acceptingActivatesTheAccountAndOpensASession() {
        post(INVITE, adminSession, new InviteProfessorRequest(NEW_PROFESSOR), Void.class);
        String token = tokenFromLastLink();

        ResponseEntity<UserResponse> accepted = accept(token, "María Gómez", "clave-nueva-1");

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody().role()).isEqualTo("PROFESSOR");
        assertThat(accepted.getBody().fullName()).isEqualTo("María Gómez");
        assertThat(sessionCookieOf(accepted)).isNotNull();

        var professor = users.findByEmailIgnoreCase(NEW_PROFESSOR).orElseThrow();
        assertThat(professor.getStatus()).isEqualTo(UserStatus.ACTIVE);

        // Ya puede entrar con su contraseña nueva.
        assertThat(rest.postForEntity("/api/v1/auth/login",
                new LoginRequest(NEW_PROFESSOR, "clave-nueva-1"), Void.class)
                .getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void anInviteIsSingleUse() {
        post(INVITE, adminSession, new InviteProfessorRequest(NEW_PROFESSOR), Void.class);
        String token = tokenFromLastLink();
        accept(token, "María Gómez", "clave-nueva-1");

        ResponseEntity<Map> second = rest.postForEntity(ACCEPT,
                new AcceptInviteRequest(token, "Otra", "clave-otra-2", null, null, null), Map.class);
        assertThat(second.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void resendingInvalidatesThePreviousInvite() {
        post(INVITE, adminSession, new InviteProfessorRequest(NEW_PROFESSOR), Void.class);
        String primero = tokenFromLastLink();
        post(INVITE, adminSession, new InviteProfessorRequest(NEW_PROFESSOR), Void.class);

        assertThat(rest.postForEntity(ACCEPT,
                new AcceptInviteRequest(primero, "María", "clave-nueva-1", null, null, null), Map.class)
                .getStatusCode().value()).isEqualTo(422);
        assertThat(accept(tokenFromLastLink(), "María Gómez", "clave-nueva-1").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void anInvalidTokenIsRejected() {
        ResponseEntity<Map> response = rest.postForEntity(ACCEPT,
                new AcceptInviteRequest("token-inventado", "X", "clave-nueva-1", null, null, null), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void theInviteInfoEndpointReturnsTheEmail() {
        post(INVITE, adminSession, new InviteProfessorRequest(NEW_PROFESSOR), Void.class);
        String token = tokenFromLastLink();

        ResponseEntity<Map> response = rest.getForEntity("/api/v1/auth/invite?token=" + token, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email")).isEqualTo(NEW_PROFESSOR);
    }

    private String sessionCookieOf(ResponseEntity<?> response) {
        var cookies = response.getHeaders().get("Set-Cookie");
        if (cookies == null) return null;
        return cookies.stream()
                .filter(c -> c.startsWith("ORION_SESSION="))
                .findFirst()
                .orElse(null);
    }
}
