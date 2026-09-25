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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.application.DocumentStorage;
import co.orion.identity.application.ProfessorInviteMailer;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.identity.persistence.TeacherApplicationRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * La invitación de profesores desde la V71 (decisiones de Pardo del 25/09/2026): no crea la cuenta,
 * el invitado se registra como aspirante con el correo de la invitación, pasa por la revisión, y si
 * la invitación era de fundador recibe el beneficio al aprobarse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, InvitacionDeProfesoresIT.Apoyo.class})
class InvitacionDeProfesoresIT extends ApiIntegrationSupport {

    private static final String INVITAR = "/api/v1/admin/professors/invite";
    private static final String VER = "/api/v1/auth/invite?token=";
    private static final String REGISTRO = "/api/v1/auth/register";
    private static final String CLAVE = "orion123*";
    private static final String CORREO = "mariana@orion.test";

    @TestConfiguration
    static class Apoyo {
        @Bean
        @Primary
        CorreoCapturado correoCapturado() {
            return new CorreoCapturado();
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
                public String signedUrl(String storageKey, String contentType, Duration ttl) {
                    return "https://res.cloudinary.test/" + storageKey;
                }
            };
        }
    }

    static class CorreoCapturado implements ProfessorInviteMailer {
        volatile String enlace;
        volatile String saludo;
        volatile String quien;

        @Override
        public void sendInvite(String toEmail, String professorName, String inviterName, String inviteLink) {
            this.enlace = inviteLink;
            this.saludo = professorName;
            this.quien = inviterName;
        }
    }

    @Autowired
    private CorreoCapturado correo;

    @Autowired
    private TeacherApplicationRepository applications;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private JdbcTemplate jdbc;

    private Session adminSession;
    private User admin;

    @BeforeEach
    void seed() {
        jdbc.update("delete from professor_invites");
        applications.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        admin = createUser("sofia@orion.test", "Sofía", UserRole.ADMIN);
        adminSession = login("sofia@orion.test");
        correo.enlace = null;
    }

    /* ---- apoyo ---- */

    private String invitar(String email, boolean fundador) {
        ResponseEntity<Void> r = post(INVITAR, adminSession,
                new InviteProfessorRequest(email, "Mariana", fundador, "directora académica"), Void.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return correo.enlace.substring(correo.enlace.lastIndexOf('/') + 1);
    }

    @SuppressWarnings("rawtypes")
    private Map ver(String token) {
        return rest.getForEntity(VER + token, Map.class).getBody();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> registrarse(String email, String token) {
        return rest.postForEntity(REGISTRO,
                new RegisterRequest("Mariana Ruiz", email, CLAVE, "+573001112244", false, true, true, true, token), Map.class);
    }

    private void completarPostulacion(Session sesion, String email) {
        assertThat(post("/api/v1/teacher-applications", sesion, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        user.changePhotoUrl("https://fotos/mariana.jpg");
        users.save(user);
        UpdateProfileRequest perfil = new UpdateProfileRequest(
                "Conversación en inglés para adultos",
                "Enseño inglés conversacional a personas adultas que ya estudiaron el idioma alguna "
                        + "vez y aun así no se atreven a hablarlo en voz alta.",
                "CO", "Bogotá", "ES", (short) 5, "Lic. en Lenguas", false, true,
                List.of(new UpdateProfileRequest.LanguageEntry("EN", false, List.of("BEGINNER"))),
                List.of("CONVERSATION"), false);
        assertThat(put("/api/v1/me/teacher-application", sesion, perfil, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        subirCv(sesion);
        post("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", sesion, null, Void.class);
        assertThat(post("/api/v1/me/teacher-application/submit", sesion, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
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
        headers.add(HttpHeaders.COOKIE, "ORION_SESSION=" + sesion.cookie() + "; XSRF-TOKEN=" + sesion.csrfToken());
        headers.add("X-XSRF-TOKEN", sesion.csrfToken());
        assertThat(rest.postForEntity("/api/v1/me/teacher-application/documents",
                new HttpEntity<>(cuerpo, headers), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void aprobar(String email) {
        UUID userId = users.findByEmailIgnoreCase(email).orElseThrow().getId();
        String id = applications.findAll().stream().filter(a -> a.getUserId().equals(userId))
                .findFirst().orElseThrow().getId().toString();
        post("/api/v1/admin/teacher-applications/" + id + "/start-review", adminSession, null, Map.class);
        assertThat(post("/api/v1/admin/teacher-applications/" + id + "/approve", adminSession, null, Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /* ---- invitar ---- */

    @Test
    @SuppressWarnings("rawtypes")
    void invitarNoCreaLaCuentaYMandaElEnlaceConNombres() {
        String token = invitar(CORREO, true);

        assertThat(users.findByEmailIgnoreCase(CORREO)).isEmpty();
        assertThat(correo.enlace).contains("/invitacion/");
        assertThat(correo.saludo).isEqualTo("Mariana");
        assertThat(correo.quien).isEqualTo("Sofía");
        // El cargo queda en la cuenta del admin, para las siguientes invitaciones.
        assertThat(users.findById(admin.getId()).orElseThrow().getJobTitle()).isEqualTo("directora académica");

        Map vista = ver(token);
        assertThat(vista.get("state")).isEqualTo("VALID");
        assertThat(vista.get("email")).isEqualTo(CORREO);
        assertThat(vista.get("professorName")).isEqualTo("Mariana");
        assertThat(vista.get("invitedByName")).isEqualTo("Sofía");
        assertThat(vista.get("invitedByTitle")).isEqualTo("directora académica");
        assertThat(vista.get("expiresAt")).isNotNull();
        assertThat((Map) vista.get("founder")).containsEntry("rateBps", 1500)
                .containsEntry("periodMonths", 3).containsEntry("baseRateBps", 2000);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void sinFundadorLaInvitacionNoTraeBeneficio() {
        Map vista = ver(invitar(CORREO, false));

        assertThat(vista.get("state")).isEqualTo("VALID");
        assertThat(vista.get("founder")).isNull();
    }

    @Test
    void unCorreoQueYaTieneCuentaNoSeInvita() {
        createUser(CORREO, "Mariana Ruiz", UserRole.STUDENT);

        assertThat(post(INVITAR, adminSession,
                new InviteProfessorRequest(CORREO, null, true, null), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void reenviarDejaSinEfectoElEnlaceAnterior() {
        String primero = invitar(CORREO, true);
        String segundo = invitar(CORREO, true);

        Map viejo = ver(primero);
        assertThat(viejo.get("state")).isEqualTo("EXPIRED");
        assertThat(viejo.get("email")).isNull();
        assertThat(ver(segundo).get("state")).isEqualTo("VALID");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void unTokenQueNoExisteSeVeVencidoSinDatos() {
        Map vista = ver("no-existe");

        assertThat(vista.get("state")).isEqualTo("EXPIRED");
        assertThat(vista.get("email")).isNull();
        assertThat(vista.get("invitedByName")).isNull();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void vencidaNoDejaRegistrarse() {
        String token = invitar(CORREO, true);
        jdbc.update("update professor_invites set expires_at = now() - interval '1 minute'");

        assertThat(ver(token).get("state")).isEqualTo("EXPIRED");
        assertThat(registrarse(CORREO, token).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(users.findByEmailIgnoreCase(CORREO)).isEmpty();
    }

    /* ---- registrarse con la invitación ---- */

    @Test
    @SuppressWarnings("rawtypes")
    void registrarseConLaInvitacionCreaUnAspiranteYLaUsa() {
        String token = invitar(CORREO, true);

        ResponseEntity<Map> alta = registrarse(CORREO, token);

        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Aspirante, aunque el formulario no dijera «quiero enseñar»: la invitación lo decide.
        assertThat(get("/api/v1/auth/me", login(CORREO), Map.class).getBody().get("role"))
                .isEqualTo("TEACHER_APPLICANT");
        Map vista = ver(token);
        assertThat(vista.get("state")).isEqualTo("USED");
        assertThat(vista.get("email")).isNull();
        // Y aceptó los Términos y la política en el registro, que antes la invitación se saltaba.
        UUID id = users.findByEmailIgnoreCase(CORREO).orElseThrow().getId();
        assertThat(jdbc.queryForObject("select count(*) from agreement_acceptances where user_id = ?",
                Integer.class, id)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void conOtroCorreoNoSeRegistraNadie() {
        String token = invitar(CORREO, true);

        assertThat(registrarse("otra@orion.test", token).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(users.findByEmailIgnoreCase("otra@orion.test")).isEmpty();
    }

    /* ---- la aprobación y el beneficio ---- */

    @Test
    void alAprobarseLaPostulacionElInvitadoFundadorRecibeElBeneficio() {
        registrarse(CORREO, invitar(CORREO, true));
        completarPostulacion(login(CORREO), CORREO);

        aprobar(CORREO);

        UUID id = users.findByEmailIgnoreCase(CORREO).orElseThrow().getId();
        var terminos = profiles.findById(id).orElseThrow().founderTerms();
        assertThat(terminos).isNotNull();
        assertThat(terminos.rateBps()).isEqualTo(1500);
        assertThat(terminos.periodMonths()).isEqualTo(3);
        assertThat(terminos.startedAt()).isNull();
    }

    @Test
    void elInvitadoSinFundadorQuedaSinBeneficio() {
        registrarse(CORREO, invitar(CORREO, false));
        completarPostulacion(login(CORREO), CORREO);

        aprobar(CORREO);

        UUID id = users.findByEmailIgnoreCase(CORREO).orElseThrow().getId();
        assertThat(profiles.findById(id).orElseThrow().founderTerms()).isNull();
    }

    /* ---- otorgar y quitar desde el admin ---- */

    @Test
    @SuppressWarnings("rawtypes")
    void elAdminOtorgaYQuitaElBeneficio() {
        User profe = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        profiles.save(new co.orion.identity.domain.ProfessorProfile(profe));

        ResponseEntity<Map> otorgado = post("/api/v1/admin/professors/" + profe.getId() + "/founder",
                adminSession, null, Map.class);
        assertThat(otorgado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(otorgado.getBody()).containsEntry("rateBps", 1500).containsEntry("status", "NOT_STARTED");

        List lista = get("/api/v1/admin/users?role=PROFESSOR", adminSession, List.class).getBody();
        assertThat((Map) ((Map) lista.getFirst()).get("founder")).containsEntry("rateBps", 1500);

        assertThat(delete("/api/v1/admin/professors/" + profe.getId() + "/founder", adminSession, Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(profiles.findById(profe.getId()).orElseThrow().founderTerms()).isNull();
    }

    @Test
    void soloUnProfesorPuedeSerFundadorYSoloElAdminLoOtorga() {
        User estudiante = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        User profe = createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        profiles.save(new co.orion.identity.domain.ProfessorProfile(profe));

        assertThat(post("/api/v1/admin/professors/" + estudiante.getId() + "/founder", adminSession, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(post("/api/v1/admin/professors/" + profe.getId() + "/founder", login("juan@orion.test"), null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
