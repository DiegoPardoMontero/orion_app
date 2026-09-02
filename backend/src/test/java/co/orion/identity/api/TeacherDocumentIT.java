package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.application.DocumentStorage;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.AdminAuditLogRepository;
import co.orion.support.ApiIntegrationSupport;

/** Documentos privados: propiedad, borrado, y la URL firmada del admin con su auditoría. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, TeacherDocumentIT.FakeStorageConfig.class})
class TeacherDocumentIT extends ApiIntegrationSupport {

    private static final String DOCS = "/api/v1/me/teacher-application/documents";

    @TestConfiguration
    static class FakeStorageConfig {
        @Bean
        @Primary
        DocumentStorage documentStorage() {
            return new DocumentStorage() {
                @Override
                public String upload(byte[] bytes, String contentType, UUID userId, String fileName) {
                    return "orion/documents/" + userId + "/fake";
                }

                @Override
                public String signedUrl(String storageKey, Duration ttl) {
                    return "https://res.cloudinary.test/" + storageKey + "?firmada";
                }
            };
        }
    }

    @Autowired
    private AdminAuditLogRepository auditLogs;

    private Session profA;
    private Session profB;
    private Session studentSession;
    private Session adminSession;
    private UUID profAId;

    @BeforeEach
    void seed() {
        users.deleteAll();
        profAId = createUser("profa@orion.test", "Profe A", UserRole.PROFESSOR).getId();
        createUser("profb@orion.test", "Profe B", UserRole.PROFESSOR);
        createUser("student@orion.test", "Estu Diante", UserRole.STUDENT);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        profA = login("profa@orion.test");
        profB = login("profb@orion.test");
        studentSession = login("student@orion.test");
        adminSession = login("admin@orion.test");
    }

    private ResponseEntity<Map> upload(Session session, byte[] bytes, MediaType partType, String docType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(partType);
        body.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "cv.pdf";
            }
        }, partHeaders));
        body.add("docType", docType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE,
                "ORION_SESSION=" + session.cookie() + "; XSRF-TOKEN=" + session.csrfToken());
        headers.add("X-XSRF-TOKEN", session.csrfToken());
        return rest.postForEntity(DOCS, new HttpEntity<>(body, headers), Map.class);
    }

    private UUID uploadCv(Session session) {
        ResponseEntity<Map> res = upload(session, new byte[] {1, 2, 3}, MediaType.APPLICATION_PDF, "CV");
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        return UUID.fromString(res.getBody().get("id").toString());
    }

    @Test
    void aProfessorUploadsAPdfDocument() {
        ResponseEntity<Map> res = upload(profA, new byte[] {1, 2, 3}, MediaType.APPLICATION_PDF, "CV");
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().get("docType")).isEqualTo("CV");
    }

    @Test
    void aNonPdfNonImageDocumentIsRejected() {
        ResponseEntity<Map> res = upload(profA, "texto".getBytes(), MediaType.TEXT_PLAIN, "CV");
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void aProfessorCannotDeleteAnotherProfessorsDocument() {
        UUID docId = uploadCv(profA);
        ResponseEntity<Map> res = delete(DOCS + "/" + docId, profB, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void aProfessorDeletesTheirOwnDocument() {
        UUID docId = uploadCv(profA);
        ResponseEntity<Void> res = delete(DOCS + "/" + docId, profA, Void.class);
        assertThat(res.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void theAdminGetsASignedUrlAndItLeavesAnAuditTrail() {
        UUID docId = uploadCv(profA);

        ResponseEntity<Map> res = get(
                "/api/v1/admin/teachers/" + profAId + "/documents/" + docId + "/url",
                adminSession, Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().get("url").toString()).contains("res.cloudinary");
        assertThat(auditLogs.findByActionAndEntityId("VIEW_DOCUMENT", docId)).hasSize(1);
    }

    @Test
    void aStudentCannotAskForTheSignedUrl() {
        UUID docId = uploadCv(profA);

        ResponseEntity<Map> res = get(
                "/api/v1/admin/teachers/" + profAId + "/documents/" + docId + "/url",
                studentSession, Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }
}
