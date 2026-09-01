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
class MeAccountIT extends ApiIntegrationSupport {

    private static final String URL = "/api/v1/me/account";

    private Session anaSession;

    @BeforeEach
    void seed() {
        users.deleteAll();
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        anaSession = login("ana@orion.test");
    }

    @Test
    void aStudentSeesTheirOwnAccount() {
        ResponseEntity<MeAccountResponse> response = get(URL, anaSession, MeAccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().email()).isEqualTo("ana@orion.test");
        assertThat(response.getBody().fullName()).isEqualTo("Ana Ramírez");
        assertThat(response.getBody().role()).isEqualTo("STUDENT");
    }

    @Test
    void updatingNameAndWhatsappPersists() {
        ResponseEntity<MeAccountResponse> updated = put(
                URL, anaSession, new UpdateAccountRequest("Ana María Ramírez", "+573001112233"),
                MeAccountResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().fullName()).isEqualTo("Ana María Ramírez");
        assertThat(updated.getBody().whatsappPhone()).isEqualTo("+573001112233");

        // Se relee y sigue igual: se guardó de verdad.
        ResponseEntity<MeAccountResponse> reread = get(URL, anaSession, MeAccountResponse.class);
        assertThat(reread.getBody().fullName()).isEqualTo("Ana María Ramírez");
        assertThat(reread.getBody().whatsappPhone()).isEqualTo("+573001112233");
    }

    @Test
    void anEmptyWhatsappClearsIt() {
        put(URL, anaSession, new UpdateAccountRequest("Ana Ramírez", "+573001112233"), MeAccountResponse.class);
        ResponseEntity<MeAccountResponse> cleared = put(
                URL, anaSession, new UpdateAccountRequest("Ana Ramírez", ""), MeAccountResponse.class);

        assertThat(cleared.getBody().whatsappPhone()).isNull();
    }

    @Test
    void aBlankNameIsRejected() {
        ResponseEntity<Map> response = put(
                URL, anaSession, new UpdateAccountRequest("", "+573001112233"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anAnonymousUserHasNoAccount() {
        ResponseEntity<Map> response = rest.getForEntity(URL, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
