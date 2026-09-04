package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.application.DocumentStorage;
import co.orion.identity.domain.SignupIntent;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/**
 * Quien se registra para enseñar no es un estudiante.
 *
 * <p>Antes lo era: la cuenta nacía idéntica a la de cualquiera que viniera a aprender, y mientras
 * su postulación esperaba una decisión podía buscar profesores y reservarles clases. Este test fija
 * las dos mitades del arreglo — que el aspirante no tiene experiencia de estudiante, y que aprobar
 * su postulación convierte de verdad la cuenta en una de profesor, que era el eslabón que faltaba
 * para que el camino terminara en alguna parte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, AspiranteAProfesorIT.SupportConfig.class})
class AspiranteAProfesorIT extends ApiIntegrationSupport {

    private static final String REGISTRO = "/api/v1/auth/register";
    private static final String MINE = "/api/v1/me/teacher-application";
    private static final String SUBMIT = "/api/v1/me/teacher-application/submit";
    private static final String ADMIN = "/api/v1/admin/teacher-applications";
    private static final String CLAVE = "orion123*";

    @TestConfiguration
    static class SupportConfig {
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

    @Autowired
    private co.orion.identity.persistence.TeacherApplicationRepository applications;

    private Session adminSession;
    private UUID adminId;

    @BeforeEach
    void seed() {
        applications.deleteAll();
        users.deleteAll();
        adminId = createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN).getId();
        adminSession = login("admin@orion.test");
    }

    /* ---- helpers ---- */

    /** Se registra por la puerta de enseñar, como quien pulsa «Postúlate para dar clases». */
    private Session registrarAspirante(String email) {
        ResponseEntity<Map> alta = rest.postForEntity(REGISTRO,
                new RegisterRequest("Aspi Rante", email, CLAVE, null, true), Map.class);
        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return login(email);
    }

    private void completarPostulacion(Session sesion, String email) {
        assertThat(post("/api/v1/teacher-applications", sesion, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.changePhotoUrl("https://fotos/aspi.jpg");
        users.save(user);

        UpdateProfileRequest perfil = new UpdateProfileRequest(
                "Conversación en inglés para adultos",
                "Enseño inglés conversacional a personas adultas que ya estudiaron el idioma alguna "
                        + "vez y aun así no se atreven a hablarlo en voz alta.",
                "CO", "Bogotá", "ES", (short) 5, "Lic. en Lenguas", false, true,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER"))),
                List.of("CONVERSATION"), false);
        assertThat(put(MINE, sesion, perfil, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        subirCv(sesion);
        assertThat(post("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", sesion, null, Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private void subirCv(Session sesion) {
        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        HttpHeaders parte = new HttpHeaders();
        parte.setContentType(MediaType.APPLICATION_PDF);
        cuerpo.add("file", new HttpEntity<>(new ByteArrayResource(new byte[] {1, 2, 3}) {
            @Override
            public String getFilename() {
                return "cv.pdf";
            }
        }, parte));
        cuerpo.add("docType", "CV");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE,
                "ORION_SESSION=" + sesion.cookie() + "; XSRF-TOKEN=" + sesion.csrfToken());
        headers.add("X-XSRF-TOKEN", sesion.csrfToken());
        assertThat(rest.postForEntity("/api/v1/me/teacher-application/documents",
                new HttpEntity<>(cuerpo, headers), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private String idDeLaPostulacionDe(String email) {
        UUID userId = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        return applications.findAll().stream()
                .filter(a -> a.getUserId().equals(userId))
                .findFirst().orElseThrow().getId().toString();
    }

    /* ---- Mientras espera ---- */

    @Test
    @SuppressWarnings("rawtypes")
    void elAspiranteNoEsUnEstudianteYSuRolEfectivoLoDice() {
        Session sesion = registrarAspirante("aspi@orion.test");

        ResponseEntity<Map> yo = get("/api/v1/auth/me", sesion, Map.class);

        assertThat(yo.getBody().get("role")).isEqualTo("TEACHER_APPLICANT");
        // En la base sigue siendo STUDENT: si lo rechazan, la cuenta ya está completa y no hay
        // nada que reparar.
        assertThat(users.findByEmailIgnoreCase("aspi@orion.test").orElseThrow().getRole())
                .isEqualTo(UserRole.STUDENT);
        assertThat(users.findByEmailIgnoreCase("aspi@orion.test").orElseThrow().getSignupIntent())
                .isEqualTo(SignupIntent.TEACH);
    }

    /**
     * La puerta que de verdad importaba: mientras esperaba, podía reservarle clases a otros
     * profesores. Toda la experiencia del estudiante cuelga de {@code ROLE_STUDENT}, así que la
     * comprobación se hace sobre varias puertas a la vez y no sobre una sola.
     */
    @Test
    @SuppressWarnings("rawtypes")
    void elAspiranteNoAlcanzaNadaDeLaExperienciaDelEstudiante() {
        Session sesion = registrarAspirante("aspi@orion.test");

        assertThat(post("/api/v1/bookings", sesion,
                Map.of("professorId", UUID.randomUUID().toString(),
                        "startsAt", "2030-01-01T18:00:00Z", "modality", "VIRTUAL"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/me/credits", sesion, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/me/progress", sesion, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/me/engagement", sesion, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/me/student-profile", sesion, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Pero sí lo suyo: su postulación, su cuenta y sus avisos. */
    @Test
    @SuppressWarnings("rawtypes")
    void elAspiranteSiAlcanzaSuPostulacionYSuCuenta() {
        Session sesion = registrarAspirante("aspi@orion.test");

        assertThat(post("/api/v1/teacher-applications", sesion, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/me/account", sesion, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/me/notifications", sesion, Object[].class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /** Quien se registró para aprender y luego postula NO pierde nada: sigue siendo estudiante. */
    @Test
    @SuppressWarnings("rawtypes")
    void unEstudianteQuePostulaSigueSiendoEstudiante() {
        ResponseEntity<Map> alta = rest.postForEntity(REGISTRO,
                new RegisterRequest("Ana Ramírez", "ana@orion.test", CLAVE, null, false), Map.class);
        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Session sesion = login("ana@orion.test");

        post("/api/v1/teacher-applications", sesion, null, Map.class);

        assertThat(get("/api/v1/auth/me", sesion, Map.class).getBody().get("role"))
                .isEqualTo("STUDENT");
        assertThat(get("/api/v1/me/credits", sesion, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /* ---- La decisión ---- */

    @Test
    @SuppressWarnings("rawtypes")
    void aprobarLaPostulacionConvierteLaCuentaEnUnaDeProfesor() {
        Session sesion = registrarAspirante("aspi@orion.test");
        completarPostulacion(sesion, "aspi@orion.test");
        assertThat(post(SUBMIT, sesion, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        String id = idDeLaPostulacionDe("aspi@orion.test");
        post(ADMIN + "/" + id + "/start-review", adminSession, null, Map.class);
        assertThat(post(ADMIN + "/" + id + "/approve", adminSession, null, Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Sin volver a entrar: el principal se refresca contra la base en cada petición.
        assertThat(get("/api/v1/auth/me", sesion, Map.class).getBody().get("role"))
                .isEqualTo("PROFESSOR");
        // Y con perfil, porque un profesor sin perfil no tendría dónde poner su tarifa.
        assertThat(get("/api/v1/me/profile", sesion, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(users.findByEmailIgnoreCase("aspi@orion.test").orElseThrow().getRole())
                .isEqualTo(UserRole.PROFESSOR);
    }

    /** Rechazada no puede significar cuenta inservible: vuelve a ser una cuenta de estudiante. */
    @Test
    @SuppressWarnings("rawtypes")
    void rechazarLaPostulacionDevuelveLaCuentaAEstudiante() {
        Session sesion = registrarAspirante("aspi@orion.test");
        completarPostulacion(sesion, "aspi@orion.test");
        post(SUBMIT, sesion, null, Map.class);

        String id = idDeLaPostulacionDe("aspi@orion.test");
        post(ADMIN + "/" + id + "/start-review", adminSession, null, Map.class);
        assertThat(post(ADMIN + "/" + id + "/reject", adminSession,
                new ReviewDecisionRequest("Nos faltan certificaciones verificables."), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(get("/api/v1/auth/me", sesion, Map.class).getBody().get("role"))
                .isEqualTo("STUDENT");
        assertThat(get("/api/v1/me/engagement", sesion, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /** El admin nunca queda fuera por esto: su rol no depende de ninguna intención. */
    @Test
    @SuppressWarnings("rawtypes")
    void elAdminSigueSiendoAdmin() {
        assertThat(get("/api/v1/auth/me", adminSession, Map.class).getBody().get("role"))
                .isEqualTo("ADMIN");
        assertThat(adminId).isNotNull();
    }
}
