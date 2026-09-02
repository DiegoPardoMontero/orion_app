package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import co.orion.identity.application.TeacherApplicationMailer;
import co.orion.identity.domain.ApplicationStatus;
import co.orion.identity.domain.TeacherApplication;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/** La máquina de estados de las postulaciones: transiciones válidas e inválidas, validación y correos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, TeacherApplicationFlowIT.SupportConfig.class})
class TeacherApplicationFlowIT extends ApiIntegrationSupport {

    private static final String APPLICATIONS = "/api/v1/teacher-applications";
    private static final String MINE = "/api/v1/me/teacher-application";
    private static final String MINE_PROFILE = "/api/v1/me/profile";
    private static final String SUBMIT = "/api/v1/me/teacher-application/submit";
    private static final String ADMIN = "/api/v1/admin/teacher-applications";

    static class CapturingMailer implements TeacherApplicationMailer {
        volatile String approved;
        volatile String changesNote;
        volatile String rejectedNote;

        @Override
        public void sendApproved(String toEmail) {
            this.approved = toEmail;
        }

        @Override
        public void sendChangesRequested(String toEmail, String note) {
            this.changesNote = note;
        }

        @Override
        public void sendRejected(String toEmail, String note) {
            this.rejectedNote = note;
        }
    }

    @TestConfiguration
    static class SupportConfig {
        @Bean
        @Primary
        CapturingMailer capturingMailer() {
            return new CapturingMailer();
        }

        @Bean
        @Primary
        DocumentStorage documentStorage() {
            return new DocumentStorage() {
                @Override
                public String upload(byte[] bytes, String contentType, UUID userId, String fileName) {
                    return "orion/documents/" + userId + "/cv";
                }

                @Override
                public String signedUrl(String storageKey, Duration ttl) {
                    return "https://res.cloudinary.test/" + storageKey;
                }
            };
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CapturingMailer mailer;

    private User aspirant;
    private Session aspirantSession;
    private Session adminSession;
    private UUID adminId;

    @BeforeEach
    void seed() {
        users.deleteAll();
        aspirant = createUser("aspirante@orion.test", "Aspi Rante", UserRole.PROFESSOR);
        adminId = createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN).getId();
        createUser("admin2@orion.test", "Otro Admin", UserRole.ADMIN);
        aspirantSession = login("aspirante@orion.test");
        adminSession = login("admin@orion.test");
        mailer.approved = null;
        mailer.changesNote = null;
        mailer.rejectedNote = null;
    }

    // --- helpers ---

    private String draftId() {
        ResponseEntity<Map> res = post(APPLICATIONS, aspirantSession, null, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        return res.getBody().get("id").toString();
    }

    private void completeProfile() {
        User u = users.findById(aspirant.getId()).orElseThrow();
        u.changePhotoUrl("https://fotos/aspi.jpg");
        users.save(u);

        UpdateProfileRequest req = new UpdateProfileRequest(
                "Profe de inglés", "Bio suficientemente larga para el perfil.", "CO", "Bogotá", "ES",
                (short) 5, "Lic. en Lenguas", false, true,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER"))),
                List.of("CONVERSATION"), false);
        assertThat(put(MINE, aspirantSession, req, Map.class).getStatusCode().value()).isEqualTo(200);

        uploadCv();
        assertThat(post("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", aspirantSession, null, Void.class)
                .getStatusCode().value()).isEqualTo(204);
    }

    private void uploadCv() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_PDF);
        body.add("file", new HttpEntity<>(new ByteArrayResource(new byte[] {1, 2, 3}) {
            @Override
            public String getFilename() {
                return "cv.pdf";
            }
        }, partHeaders));
        body.add("docType", "CV");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE,
                "ORION_SESSION=" + aspirantSession.cookie() + "; XSRF-TOKEN=" + aspirantSession.csrfToken());
        headers.add("X-XSRF-TOKEN", aspirantSession.csrfToken());
        ResponseEntity<Map> res = rest.postForEntity(
                "/api/v1/me/teacher-application/documents", new HttpEntity<>(body, headers), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }

    // --- tests ---

    @Test
    void submittingAnEmptyDraftReturnsTheFullListOfMissingRequirements() {
        draftId();
        ResponseEntity<Map> res = post(SUBMIT, aspirantSession, null, Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) res.getBody().get("missing");
        assertThat(missing).contains("photo", "bio", "language", "goal", "cv", "agreement");
    }

    @Test
    void submittingWithoutTheCvListsExactlyTheCvAsMissing() {
        draftId();
        User u = users.findById(aspirant.getId()).orElseThrow();
        u.changePhotoUrl("https://fotos/aspi.jpg");
        users.save(u);
        UpdateProfileRequest req = new UpdateProfileRequest(
                "Profe", "Bio larga del perfil.", "CO", "Bogotá", "ES", (short) 5, "Educación", false, true,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER"))),
                List.of("CONVERSATION"), false);
        put(MINE, aspirantSession, req, Map.class);
        post("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", aspirantSession, null, Void.class);

        ResponseEntity<Map> res = post(SUBMIT, aspirantSession, null, Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) res.getBody().get("missing");
        assertThat(missing).containsExactly("cv");
    }

    @Test
    void approvingADraftIsAConflict() {
        String id = draftId();
        ResponseEntity<Map> res = post(ADMIN + "/" + id + "/approve", adminSession, null, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void theHappyPathTakesTheApplicationFromDraftToApproved() {
        String id = draftId();
        completeProfile();

        assertThat(post(SUBMIT, aspirantSession, null, Map.class).getStatusCode().value()).isEqualTo(200);
        assertThat(post(ADMIN + "/" + id + "/start-review", adminSession, null, Void.class)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(post(ADMIN + "/" + id + "/approve", adminSession, null, Void.class)
                .getStatusCode().value()).isEqualTo(204);

        ResponseEntity<Map> mine = get(MINE, aspirantSession, Map.class);
        assertThat(mine.getBody().get("status")).isEqualTo("APPROVED");
        assertThat(mailer.approved).isEqualTo("aspirante@orion.test");
    }

    @Test
    void rejectingRequiresAReasonOfAtLeastTenCharacters() {
        String id = draftId();
        completeProfile();
        post(SUBMIT, aspirantSession, null, Map.class);
        post(ADMIN + "/" + id + "/start-review", adminSession, null, Void.class);

        assertThat(post(ADMIN + "/" + id + "/reject", adminSession, new ReviewDecisionRequest("corto"), Map.class)
                .getStatusCode().value()).isEqualTo(400);

        assertThat(post(ADMIN + "/" + id + "/reject", adminSession,
                new ReviewDecisionRequest("No cumple el perfil mínimo requerido"), Void.class)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(get(MINE, aspirantSession, Map.class).getBody().get("status")).isEqualTo("REJECTED");
        assertThat(mailer.rejectedNote).contains("perfil mínimo");
    }

    @Test
    void requestingChangesSendsItBackAndItCanBeResubmitted() {
        String id = draftId();
        completeProfile();
        post(SUBMIT, aspirantSession, null, Map.class);
        post(ADMIN + "/" + id + "/start-review", adminSession, null, Void.class);

        assertThat(post(ADMIN + "/" + id + "/request-changes", adminSession,
                new ReviewDecisionRequest("Sube un CV más detallado, por favor"), Void.class)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(get(MINE, aspirantSession, Map.class).getBody().get("status"))
                .isEqualTo("CHANGES_REQUESTED");
        assertThat(mailer.changesNote).contains("CV más detallado");

        // Reenvío: vuelve a PENDING_REVIEW.
        assertThat(post(SUBMIT, aspirantSession, null, Map.class).getStatusCode().value()).isEqualTo(200);
        assertThat(get(MINE, aspirantSession, Map.class).getBody().get("status"))
                .isEqualTo("PENDING_REVIEW");
    }

    @Test
    void onceApprovedTheProfessorCanPublishAndAppearsInTheDirectory() {
        String id = draftId();
        completeProfile();
        post(SUBMIT, aspirantSession, null, Map.class);
        post(ADMIN + "/" + id + "/start-review", adminSession, null, Void.class);
        post(ADMIN + "/" + id + "/approve", adminSession, null, Void.class);

        // Antes de aprobar no podía publicar; ahora sí. Fija tarifa y publica.
        assertThat(put("/api/v1/me/profile/rate", aspirantSession, new RateRequest(60000L), Map.class)
                .getStatusCode().value()).isEqualTo(200);
        UpdateProfileRequest publish = new UpdateProfileRequest(
                "Profe de inglés", "Bio suficientemente larga para el perfil.", "CO", "Bogotá", "ES",
                (short) 5, "Lic. en Lenguas", false, true,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER"))),
                List.of("CONVERSATION"), true);
        assertThat(put(MINE_PROFILE, aspirantSession, publish, Map.class).getStatusCode().value())
                .isEqualTo(200);

        ResponseEntity<PagedProfessors> directory =
                get("/api/v1/professors", aspirantSession, PagedProfessors.class);
        assertThat(directory.getBody().content()).hasSize(1);
        assertThat(directory.getBody().content().get(0).id()).isEqualTo(aspirant.getId());
    }

    @Test
    void anAdminCannotApproveTheirOwnApplication() {
        // Postulación propia del admin, sembrada directamente en UNDER_REVIEW.
        TeacherApplication own = teacherApplications.saveAndFlush(
                new TeacherApplication(adminId, ApplicationStatus.UNDER_REVIEW, null, null));

        ResponseEntity<Map> res = post(ADMIN + "/" + own.getId() + "/approve", adminSession, null, Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }
}
