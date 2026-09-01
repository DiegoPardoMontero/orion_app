package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.support.ApiIntegrationSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ProfessorDirectoryIT extends ApiIntegrationSupport {

    private static final String PROFESSORS = "/api/v1/professors";
    private static final String MY_PROFILE = "/api/v1/me/profile";

    @Autowired
    private ProfessorProfileRepository profiles;

    private User maria;
    private User juan;
    private Session anaSession;
    private Session mariaSession;

    @BeforeEach
    void seed() {
        profiles.deleteAll();
        users.deleteAll();

        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        juan = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);

        ProfessorProfile mariaProfile = new ProfessorProfile(maria);
        mariaProfile.describe("Inglés conversacional", "Diez años de experiencia.");
        mariaProfile.publish();
        profiles.save(mariaProfile);

        // Juan tiene perfil, pero sin publicar.
        profiles.save(new ProfessorProfile(juan));

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
    }

    @Test
    void theDirectoryOnlyListsPublishedProfessors() {
        ResponseEntity<ProfessorSummary[]> response = get(PROFESSORS, anaSession, ProfessorSummary[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].fullName()).isEqualTo("María Gómez");
        assertThat(response.getBody()[0].headline()).isEqualTo("Inglés conversacional");
    }

    @Test
    void aStudentCanReadThePublishedDetail() {
        ResponseEntity<ProfessorDetail> response = get(
                PROFESSORS + "/" + maria.getId(), anaSession, ProfessorDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().bio()).isEqualTo("Diez años de experiencia.");
    }

    @Test
    void theDetailOfAnUnpublishedProfessorIsNotFound() {
        // Juan existe y tiene perfil: aun así responde 404, no revelamos perfiles ocultos.
        ResponseEntity<Map> response = get(PROFESSORS + "/" + juan.getId(), anaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theDetailOfAnUnknownProfessorIsNotFound() {
        ResponseEntity<Map> response = get(PROFESSORS + "/" + UUID.randomUUID(), anaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unpublishingRemovesTheProfessorFromTheDirectoryAndFromTheDetail() {
        ResponseEntity<ProfileResponse> updated = put(
                MY_PROFILE, mariaSession,
                new UpdateProfileRequest("Inglés conversacional", "Diez años de experiencia.", false),
                ProfileResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().isPublished()).isFalse();

        assertThat(get(PROFESSORS, anaSession, ProfessorSummary[].class).getBody()).isEmpty();
        assertThat(get(PROFESSORS + "/" + maria.getId(), anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void republishingBringsTheProfessorBack() {
        // La foto ahora vive en el usuario (se sube por /me/photo); la fijamos directo para
        // comprobar que fluye al directorio, sin depender del update del perfil.
        User m = users.findById(maria.getId()).orElseThrow();
        m.changePhotoUrl("https://fotos/maria.jpg");
        users.save(m);

        put(MY_PROFILE, mariaSession,
                new UpdateProfileRequest("Inglés de negocios", "Nueva bio.", true),
                ProfileResponse.class);

        ResponseEntity<ProfessorSummary[]> response = get(PROFESSORS, anaSession, ProfessorSummary[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].headline()).isEqualTo("Inglés de negocios");
        assertThat(response.getBody()[0].photoUrl()).isEqualTo("https://fotos/maria.jpg");
    }

    @Test
    void aProfessorReadsTheirOwnProfileEvenWhenUnpublished() {
        Session juanSession = login("juan@orion.test");

        ResponseEntity<ProfileResponse> response = get(MY_PROFILE, juanSession, ProfileResponse.class);

        // Juan no está publicado: GET /professors/{id} le daría 404. Su propio perfil sí lo ve,
        // porque si no, no podría editarlo para publicarse por primera vez.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(juan.getId());
        assertThat(response.getBody().isPublished()).isFalse();
    }

    @Test
    void aStudentCannotReadTheProfessorProfileEndpoint() {
        ResponseEntity<Map> response = get(MY_PROFILE, anaSession, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aStudentCannotUpdateAProfessorProfile() {
        ResponseEntity<Map> response = put(
                MY_PROFILE, anaSession,
                new UpdateProfileRequest("Me autoproclamo profesora", null, true),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aDeactivatedProfessorDisappearsFromTheDirectoryEvenWhilePublished() {
        maria.deactivate();
        users.save(maria);

        assertThat(get(PROFESSORS, anaSession, ProfessorSummary[].class).getBody()).isEmpty();
        assertThat(get(PROFESSORS + "/" + maria.getId(), anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
