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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.application.PhotoUploader;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, MePhotoIT.MockUploaderConfig.class})
class MePhotoIT extends ApiIntegrationSupport {

    private static final String URL = "/api/v1/me/photo";

    /** Uploader simulado: no llama a Cloudinary, devuelve una URL predecible. */
    @TestConfiguration
    static class MockUploaderConfig {
        @Bean
        @Primary
        PhotoUploader uploader() {
            return (bytes, contentType) -> "https://cdn.orion.test/avatar.jpg";
        }
    }

    private User ana;
    private Session anaSession;

    @BeforeEach
    void seed() {
        users.deleteAll();
        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        anaSession = login("ana@orion.test");
    }

    private ResponseEntity<Map> upload(Session session, byte[] bytes, MediaType partType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(partType);
        body.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "foto";
            }
        }, partHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (session != null) {
            headers.add(HttpHeaders.COOKIE,
                    "ORION_SESSION=" + session.cookie() + "; XSRF-TOKEN=" + session.csrfToken());
            headers.add("X-XSRF-TOKEN", session.csrfToken());
        }
        return rest.postForEntity(URL, new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    void aValidImageIsUploadedAndPersisted() {
        ResponseEntity<Map> response = upload(anaSession, new byte[] {1, 2, 3, 4}, MediaType.IMAGE_JPEG);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("photoUrl")).isEqualTo("https://cdn.orion.test/avatar.jpg");
        assertThat(users.findById(ana.getId()).orElseThrow().getPhotoUrl())
                .isEqualTo("https://cdn.orion.test/avatar.jpg");
    }

    @Test
    void aNonImageTypeIsRejected() {
        ResponseEntity<Map> response = upload(anaSession, "no soy imagen".getBytes(), MediaType.TEXT_PLAIN);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void anAnonymousUploadIsRejected() {
        // Sin sesión no hay token CSRF: el filtro CSRF corre antes que el de autenticación → 403.
        ResponseEntity<Map> response = upload(null, new byte[] {1, 2, 3}, MediaType.IMAGE_PNG);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }
}
