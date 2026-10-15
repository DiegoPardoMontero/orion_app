package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.application.DocumentStorage;
import co.orion.identity.domain.DocumentType;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.TeacherDocument;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherDocumentRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * La habilitación para adolescentes (Bloque 12): el profe acepta el código y sube los dos documentos; el
 * admin la enciende por un año; el buscador para un adolescente solo muestra a los habilitados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class HabilitacionParaAdolescentesIT extends ApiIntegrationSupport {

    @Autowired private ProfessorProfileRepository profiles;
    @Autowired private TeacherDocumentRepository documents;
    @Autowired private JdbcTemplate jdbc;

    private User maria;
    private User juan;
    private Session sesionMaria;
    private Session admin;

    @BeforeEach
    void seed() {
        jdbc.update("delete from teacher_documents");
        profiles.deleteAll();
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        for (User u : List.of(maria, juan)) {
            ProfessorProfile p = new ProfessorProfile(u);
            p.describe("Inglés conversacional para adultos que ya estudiaron",
                    "Soy profesor de inglés desde hace seis años. Trabajo con adultos que ya estudiaron el idioma alguna vez y aun así no se atreven a hablarlo. Practicamos desde la primera clase.");
            p.changeRate(50_000L);
            p.publish();
            profiles.save(p);
            approveTeacher(u.getId());
        }
        sesionMaria = login("maria@orion.test");
        admin = login("admin@orion.test");
    }

    @TestConfiguration
    static class FakeStorageConfig {
        @Bean
        @Primary
        DocumentStorage documentStorage() {
            return new DocumentStorage() {
                @Override
                public String upload(byte[] bytes, String contentType, UUID userId, String fileName) {
                    return "orion/documents/" + userId + "/" + fileName;
                }

                @Override
                public String signedUrl(String storageKey, String contentType, Duration ttl) {
                    return "https://res.cloudinary.test/" + storageKey;
                }
            };
        }
    }

    /** Sube un certificado por el endpoint de Mi perfil, como lo hace la pantalla. */
    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> subir(Session session, String docType, String nombre) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_PDF);
        body.add("file", new HttpEntity<>(new ByteArrayResource("%PDF".getBytes()) {
            @Override
            public String getFilename() {
                return nombre;
            }
        }, partHeaders));
        body.add("docType", docType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, "ORION_SESSION=" + session.cookie() + "; XSRF-TOKEN=" + session.csrfToken());
        headers.add("X-XSRF-TOKEN", session.csrfToken());
        return rest.postForEntity("/api/v1/me/profile/minors/documents", new HttpEntity<>(body, headers), Map.class);
    }

    /**
     * Un profe aprobado sube los dos certificados desde Mi perfil, aunque ya tenga los seis documentos
     * de su postulación (15/10/2026: por el endpoint de la postulación recibían un 409).
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    @DisplayName("Un profe aprobado sube y quita los certificados desde Mi perfil, sin pasar por la postulación")
    void subeLosCertificadosDesdeMiPerfil() {
        for (int i = 0; i < 6; i++) {
            documents.save(new TeacherDocument(maria.getId(), null, DocumentType.OTHER, "otro" + i + ".pdf", "o" + i, "application/pdf", 10));
        }
        assertThat(subir(sesionMaria, "CV", "cv.pdf").getStatusCode().value()).isEqualTo(400);

        ResponseEntity<Map> registro = subir(sesionMaria, "REGISTRY_CHECK", "registro.pdf");
        assertThat(registro.getStatusCode().value()).isEqualTo(200);
        assertThat(registro.getBody().get("registryCheck")).isEqualTo(true);
        ResponseEntity<Map> antecedentes = subir(sesionMaria, "BACKGROUND_CHECK", "antecedentes.pdf");
        assertThat(antecedentes.getBody().get("backgroundCheck")).isEqualTo(true);
        List<Map<String, Object>> subidos = (List<Map<String, Object>>) antecedentes.getBody().get("documents");
        assertThat(subidos).extracting(d -> d.get("docType")).containsExactlyInAnyOrder("REGISTRY_CHECK", "BACKGROUND_CHECK");

        // Quitar el suyo; el de otro, no existe.
        String id = subidos.get(0).get("id").toString();
        assertThat(delete("/api/v1/me/profile/minors/documents/" + id, login("juan@orion.test"), Map.class).getStatusCode().value()).isEqualTo(404);
        ResponseEntity<Map> quitado = delete("/api/v1/me/profile/minors/documents/" + id, sesionMaria, Map.class);
        assertThat(quitado.getStatusCode().value()).isEqualTo(200);
        assertThat((List<?>) quitado.getBody().get("documents")).hasSize(1);
    }

    private void documentos(UUID userId) {
        documents.save(new TeacherDocument(userId, null, DocumentType.REGISTRY_CHECK, "registro.pdf", "k1", "application/pdf", 10));
        documents.save(new TeacherDocument(userId, null, DocumentType.BACKGROUND_CHECK, "antecedentes.pdf", "k2", "application/pdf", 10));
    }

    @Test
    @SuppressWarnings("rawtypes")
    @DisplayName("Sin el código y los dos documentos el admin no puede habilitar; con ellos, queda un año")
    void elCaminoCompleto() {
        ResponseEntity<MinorsClearanceView> antes = get("/api/v1/me/profile/minors", sesionMaria, MinorsClearanceView.class);
        assertThat(antes.getBody().active()).isFalse();
        assertThat(antes.getBody().missing()).hasSize(3);

        ResponseEntity<Map> temprano = post("/api/v1/admin/professors/" + maria.getId() + "/minors", admin, null, Map.class);
        assertThat(temprano.getStatusCode().value()).isEqualTo(400);
        assertThat(temprano.getBody().get("error").toString()).contains("código de conducta");

        assertThat(post("/api/v1/me/profile/minors/conduct", sesionMaria, null, MinorsClearanceView.class)
                .getBody().conductAccepted()).isTrue();
        documentos(maria.getId());

        ResponseEntity<MinorsClearanceView> habilitada = post("/api/v1/admin/professors/" + maria.getId() + "/minors",
                admin, null, MinorsClearanceView.class);
        assertThat(habilitada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(habilitada.getBody().active()).isTrue();
        assertThat(habilitada.getBody().clearedUntil()).isAfter(java.time.LocalDate.now().plusMonths(11));

        // Se ve en su perfil, en el público y en Usuarios.
        assertThat(get("/api/v1/me/profile", sesionMaria, ProfileResponse.class).getBody().minors().active()).isTrue();
        assertThat(rest.getForEntity("/api/v1/professors/" + maria.getId(), ProfessorDetail.class).getBody().minorsCleared()).isTrue();
        assertThat(rest.getForEntity("/api/v1/professors/" + juan.getId(), ProfessorDetail.class).getBody().minorsCleared()).isFalse();
        ResponseEntity<AdminUserResponse[]> usuarios = get("/api/v1/admin/users?role=PROFESSOR", admin, AdminUserResponse[].class);
        assertThat(usuarios.getBody()).filteredOn(u -> u.id().equals(maria.getId())).singleElement()
                .satisfies(u -> assertThat(u.minors().active()).isTrue());

        // El buscador para un adolescente solo la muestra a ella; el de siempre, a los dos.
        PagedProfessors paraMenor = rest.getForEntity("/api/v1/professors?forMinor=true", PagedProfessors.class).getBody();
        assertThat(paraMenor.content()).extracting(ProfessorCard::id).containsExactly(maria.getId());
        assertThat(rest.getForEntity("/api/v1/professors", PagedProfessors.class).getBody().totalElements()).isEqualTo(2);

        // Apagarla la quita del buscador para adolescentes.
        assertThat(delete("/api/v1/admin/professors/" + maria.getId() + "/minors", admin, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/api/v1/professors?forMinor=true", PagedProfessors.class).getBody().totalElements()).isZero();
    }

    @Test
    @DisplayName("Vencida al año, deja de contar")
    void vencida() {
        post("/api/v1/me/profile/minors/conduct", sesionMaria, null, MinorsClearanceView.class);
        documentos(maria.getId());
        post("/api/v1/admin/professors/" + maria.getId() + "/minors", admin, null, MinorsClearanceView.class);
        jdbc.update("update professor_profiles set minors_cleared_until = current_date - 1 where user_id = ?", maria.getId());

        assertThat(get("/api/v1/me/profile/minors", sesionMaria, MinorsClearanceView.class).getBody().active()).isFalse();
        assertThat(rest.getForEntity("/api/v1/professors?forMinor=true", PagedProfessors.class).getBody().totalElements()).isZero();
    }

    @Test
    void unEstudianteNoAceptaElCodigo() {
        assertThat(post("/api/v1/me/profile/minors/conduct", login("ana@orion.test"), null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
