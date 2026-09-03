package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    /** La ficha cumple los mínimos de palabras del dominio; las afirmaciones la comparan por constante. */
    private static final String TITULAR = "Profesora de inglés conversacional para adultos";
    private static final String OTRO_TITULAR = "Inglés de negocios para equipos comerciales";
    private static final String BIO =
            "Enseño inglés conversacional a adultos que ya estudiaron el idioma alguna vez y aun así no se atreven a hablarlo. Practicamos desde la primera clase con temas que te importan de verdad.";

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
        mariaProfile.describe(TITULAR, BIO);
        // Bajo COMMISSION (default), sin tarifa no aparecería: se la fijamos para que sea visible.
        mariaProfile.changeRate(50000L);
        mariaProfile.publish();
        profiles.save(mariaProfile);

        // Juan tiene perfil, pero sin publicar.
        profiles.save(new ProfessorProfile(juan));

        // Ambos aprobados: el gate de visibilidad los deja pasar. Que aparezcan o no en el directorio
        // depende ya solo de publicar/tarifa (María sí; Juan no).
        approveTeacher(maria.getId());
        approveTeacher(juan.getId());

        anaSession = login("ana@orion.test");
        mariaSession = login("maria@orion.test");
    }

    private UpdateProfileRequest profileRequest(String headline, String bio, boolean published) {
        return new UpdateProfileRequest(headline, bio, null, null, null, null, null, false, true,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER"))),
                List.of("CONVERSATION"), published);
    }

    @Test
    void theDirectoryOnlyListsPublishedProfessors() {
        ResponseEntity<PagedProfessors> response = get(PROFESSORS, anaSession, PagedProfessors.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().get(0).fullName()).isEqualTo("María Gómez");
        assertThat(response.getBody().content().get(0).headline()).isEqualTo(TITULAR);
        assertThat(response.getBody().content().get(0).hourlyRateCop()).isEqualTo(50000L);
    }

    @Test
    void theDirectoryIsPublic() {
        // Sin sesión: el marketplace se ve anónimo.
        ResponseEntity<PagedProfessors> response = rest.getForEntity(PROFESSORS, PagedProfessors.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(1);
    }

    @Test
    void aStudentCanReadThePublishedDetail() {
        ResponseEntity<ProfessorDetail> response = get(
                PROFESSORS + "/" + maria.getId(), anaSession, ProfessorDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().bio()).isEqualTo(BIO);
        assertThat(response.getBody().hourlyRateCop()).isEqualTo(50000L);
    }

    @Test
    void theDetailOfAnUnpublishedProfessorIsNotFound() {
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
                profileRequest(TITULAR, BIO, false),
                ProfileResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().isPublished()).isFalse();

        assertThat(get(PROFESSORS, anaSession, PagedProfessors.class).getBody().content()).isEmpty();
        assertThat(get(PROFESSORS + "/" + maria.getId(), anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void republishingBringsTheProfessorBack() {
        User m = users.findById(maria.getId()).orElseThrow();
        m.changePhotoUrl("https://fotos/maria.jpg");
        users.save(m);

        put(MY_PROFILE, mariaSession,
                profileRequest(OTRO_TITULAR, BIO, true),
                ProfileResponse.class);

        ResponseEntity<PagedProfessors> response = get(PROFESSORS, anaSession, PagedProfessors.class);

        assertThat(response.getBody().content()).hasSize(1);
        assertThat(response.getBody().content().get(0).headline()).isEqualTo(OTRO_TITULAR);
        assertThat(response.getBody().content().get(0).photoUrl()).isEqualTo("https://fotos/maria.jpg");
    }

    @Test
    void publishingWithoutARateIsRejected() {
        // Juan no tiene tarifa: publicar bajo COMMISSION debe fallar con 422 (chequeo amable).
        // La ficha va completa a propósito: con textos demasiado cortos saldría el mismo 422 por
        // otro motivo y el test pasaría sin comprobar nada.
        Session juanSession = login("juan@orion.test");
        ResponseEntity<Map> response = put(MY_PROFILE, juanSession,
                profileRequest("Conversación en inglés para adultos",
                        "Enseño inglés conversacional a adultos que ya estudiaron el idioma alguna "
                                + "vez y aun así no se atreven a hablarlo. Practicamos desde la "
                                + "primera clase.",
                        true),
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(get(PROFESSORS, anaSession, PagedProfessors.class).getBody().content()).hasSize(1);
    }

    @Test
    void aProfessorReadsTheirOwnProfileEvenWhenUnpublished() {
        Session juanSession = login("juan@orion.test");

        ResponseEntity<ProfileResponse> response = get(MY_PROFILE, juanSession, ProfileResponse.class);

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
    void aStudentCannotSetARate() {
        ResponseEntity<Map> response = put(MY_PROFILE + "/rate", anaSession, new RateRequest(50000L), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aStudentCannotUpdateAProfessorProfile() {
        ResponseEntity<Map> response = put(
                MY_PROFILE, anaSession,
                profileRequest("Me autoproclamo profesora", null, true),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aDeactivatedProfessorDisappearsFromTheDirectoryEvenWhilePublished() {
        maria.deactivate();
        users.save(maria);

        assertThat(get(PROFESSORS, anaSession, PagedProfessors.class).getBody().content()).isEmpty();
        assertThat(get(PROFESSORS + "/" + maria.getId(), anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
