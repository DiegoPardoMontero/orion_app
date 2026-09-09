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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.application.DocumentStorage;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/**
 * Que volver a la postulación no cueste la postulación.
 *
 * <p>El aspirante guarda su perfil en {@code professor_profiles}, pero su rol efectivo es
 * TEACHER_APPLICANT y {@code /me/profile} exige PROFESSOR: no tenía forma de leer lo suyo. El
 * wizard se dibujaba vacío y, al pasar de paso, mandaba ese vacío — y el guardado reemplazaba en
 * vez de fusionar, así que idiomas, objetivos, ciudad y estudios se borraban de verdad. Pasaba en
 * cualquier regreso, pero se notaba al pedir cambios, que es el regreso obligatorio.
 *
 * <p>Los tests de antes no lo veían porque creaban al aspirante con rol PROFESSOR.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, PostulacionConservaDatosIT.SinCloudinary.class})
class PostulacionConservaDatosIT extends ApiIntegrationSupport {

    private static final String MINE = "/api/v1/me/teacher-application";
    private static final String ASPIRANTE = "aspirante@orion.test";

    @TestConfiguration
    static class SinCloudinary {
        @Bean
        @Primary
        DocumentStorage documentStorage() {
            return new DocumentStorage() {
                @Override
                public String upload(byte[] bytes, String contentType, UUID userId, String fileName) {
                    return "orion/documents/" + userId + "/cv";
                }

                @Override
                public String signedUrl(String storageKey, String contentType, Duration ttl) {
                    return "https://res.cloudinary.test/" + storageKey;
                }
            };
        }
    }

    private Session aspirante;
    private Session admin;

    @BeforeEach
    void seed() {
        users.deleteAll();
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        admin = login("admin@orion.test");

        // Por la puerta de enseñar: la cuenta nace como aspirante, no como profesor.
        ResponseEntity<Map> alta = rest.postForEntity("/api/v1/auth/register",
                new RegisterRequest("Aspi Rante", ASPIRANTE, PASSWORD, null, true, true, true, true),
                Map.class);
        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        aspirante = login(ASPIRANTE);

        assertThat(post("/api/v1/teacher-applications", aspirante, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put(MINE, aspirante, postulacionCompleta(), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Lo primero que le faltaba: poder leer lo que él mismo escribió. */
    @Test
    void theApplicationGivesTheApplicantBackWhatTheyAnswered() {
        TeacherApplicationView vista = get(MINE, aspirante, TeacherApplicationView.class).getBody();

        assertThat(vista).isNotNull();
        assertThat(vista.answers()).isNotNull();
        assertThat(vista.answers().headline()).isEqualTo("Conversación en inglés para adultos");
        assertThat(vista.answers().city()).isEqualTo("Bogotá");
        assertThat(vista.answers().education()).isEqualTo("Lic. en Lenguas");
        assertThat(vista.answers().yearsExperience()).isEqualTo((short) 5);
        assertThat(vista.answers().acceptsTrial()).isTrue();
        assertThat(vista.answers().languages()).singleElement()
                .satisfies(l -> assertThat(l.code()).isEqualTo("EN"));
        assertThat(vista.answers().goals()).containsExactly("CONVERSATION");
    }

    /**
     * El guardado del wizard es «guardar el avance», no «reemplazar la postulación»: manda el paso
     * que se acaba de tocar. Lo que no viene, se queda.
     */
    @Test
    void savingOneStepDoesNotWipeTheOthers() {
        UpdateProfileRequest soloElPasoUno = new UpdateProfileRequest(
                "Conversación en inglés para adultos de verdad",
                "Enseño inglés conversacional a personas adultas que ya estudiaron el idioma alguna "
                        + "vez y aun así no se atreven a hablarlo en voz alta.",
                null, null, null, null, null, null, null, null, null, null);

        assertThat(put(MINE, aspirante, soloElPasoUno, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ProfileResponse respuestas = get(MINE, aspirante, TeacherApplicationView.class)
                .getBody().answers();
        assertThat(respuestas.headline()).isEqualTo("Conversación en inglés para adultos de verdad");
        // Nada de esto viajaba en el cuerpo, y nada de esto puede haber desaparecido.
        assertThat(respuestas.city()).isEqualTo("Bogotá");
        assertThat(respuestas.countryCode()).isEqualTo("CO");
        assertThat(respuestas.education()).isEqualTo("Lic. en Lenguas");
        assertThat(respuestas.yearsExperience()).isEqualTo((short) 5);
        assertThat(respuestas.acceptsTrial()).isTrue();
        assertThat(respuestas.languages()).hasSize(1);
        assertThat(respuestas.goals()).containsExactly("CONVERSATION");
    }

    /** Mandar solo los idiomas no puede llevarse por delante los objetivos, ni al revés. */
    @Test
    void savingOnlyTheLanguagesKeepsTheGoals() {
        UpdateProfileRequest soloIdiomas = new UpdateProfileRequest(
                null, null, null, null, null, null, null, null, null,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER")),
                        new UpdateProfileRequest.LanguageEntry("FR", false, List.of("BEGINNER"))),
                null, null);

        assertThat(put(MINE, aspirante, soloIdiomas, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ProfileResponse respuestas = get(MINE, aspirante, TeacherApplicationView.class)
                .getBody().answers();
        assertThat(respuestas.languages()).hasSize(2);
        assertThat(respuestas.goals()).containsExactly("CONVERSATION");
    }

    /** El regreso obligatorio: le piden cambios y su postulación sigue entera. */
    @Test
    void askingForChangesKeepsEverythingTheApplicantHadUploaded() {
        assertThat(post("/api/v1/me/teacher-application/submit", aspirante, null, Map.class)
                .getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST);

        UUID id = get(MINE, aspirante, TeacherApplicationView.class).getBody().id();
        post("/api/v1/admin/teacher-applications/" + id + "/request-changes", admin,
                Map.of("note", "Sube un certificado de idioma, por favor."), Map.class);

        TeacherApplicationView vista = get(MINE, aspirante, TeacherApplicationView.class).getBody();
        assertThat(vista.answers().city()).isEqualTo("Bogotá");
        assertThat(vista.answers().languages()).hasSize(1);
        assertThat(vista.answers().goals()).containsExactly("CONVERSATION");
        assertThat(vista.answers().headline()).isEqualTo("Conversación en inglés para adultos");
    }

    private UpdateProfileRequest postulacionCompleta() {
        return new UpdateProfileRequest(
                "Conversación en inglés para adultos",
                "Enseño inglés conversacional a personas adultas que ya estudiaron el idioma alguna "
                        + "vez y aun así no se atreven a hablarlo en voz alta.",
                "CO", "Bogotá", "ES", (short) 5, "Lic. en Lenguas", false, true,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER"))),
                List.of("CONVERSATION"), false);
    }
}
