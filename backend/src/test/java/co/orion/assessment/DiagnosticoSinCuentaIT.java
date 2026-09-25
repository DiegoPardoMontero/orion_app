package co.orion.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.assessment.application.LeadRetentionJob;
import co.orion.assessment.domain.AssessmentCompletedEvent;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.shared.security.IntentosDeAcceso;
import co.orion.support.ApiIntegrationSupport;

/**
 * El diagnóstico sin cuenta (Pardo, 22/09/2026): nombre, dos casillas y a hablar.
 *
 * <p>Lo que se prueba es lo que lo hace seguro de abrir al público: que la llave del dispositivo
 * solo abre lo suyo, que nadie entra sin las dos declaraciones, que al crear la cuenta todo se muda
 * a ella —autorización de voz incluida, con su fecha original— y que lo que nadie reclama se borra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, DiagnosticoSinCuentaIT.Mudanzas.class})
class DiagnosticoSinCuentaIT extends ApiIntegrationSupport {

    /** Cuenta las mudanzas que terminan con un diagnóstico cerrado: cada una publica este evento. */
    @TestConfiguration
    static class Mudanzas {
        static final List<UUID> CUENTAS = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(AssessmentCompletedEvent evento) {
            CUENTAS.add(evento.userId());
        }
    }

    /** Doble envío: el token CSRF es el que diga la cookie, y la cabecera tiene que coincidir. */
    private static final String CSRF = "csrf-de-prueba";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LeadRetentionJob retencion;

    @Autowired
    private IntentosDeAcceso intentos;

    @BeforeEach
    void seed() {
        jdbc.update("delete from assessment_turns");
        jdbc.update("delete from assessment_recommendations");
        jdbc.update("delete from confidence_assessments");
        jdbc.update("delete from assessment_leads");
        jdbc.update("delete from voice_consents");
        users.deleteAll();
        intentos.olvidarTodo();
        jdbc.update("update platform_settings set value = 'true' where key = 'assessment_enabled'");
    }

    private HttpHeaders anonimo(String llave) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + CSRF + (llave == null ? "" : "; ORION_LEAD=" + llave));
        h.add("X-XSRF-TOKEN", CSRF);
        return h;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> comoLead(HttpMethod metodo, String path, String llave, Object cuerpo) {
        return rest.exchange(path, metodo, new HttpEntity<>(cuerpo, anonimo(llave)), Map.class);
    }

    /** Crea el lead y devuelve la llave que quedó en la cookie. */
    @SuppressWarnings("rawtypes")
    private String nuevoLead(String nombre) {
        ResponseEntity<Map> r = comoLead(HttpMethod.POST, "/api/v1/assessment-leads", null,
                Map.of("firstName", nombre, "adult", true, "acceptsVoice", true));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String cookie = r.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("ORION_LEAD=")).findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Lax");
        return cookie.substring("ORION_LEAD=".length(), cookie.indexOf(';'));
    }

    /** Una conversación de cuatro turnos en inglés, cerrada: la que da número. */
    @SuppressWarnings("rawtypes")
    private String conversacionCompleta(String llave) {
        String id = (String) comoLead(HttpMethod.POST, "/api/v1/assessments", llave,
                Map.of("languageCode", "EN")).getBody().get("assessmentId");
        String[] dichos = {
            "Okay hi, my name is Eduardo and I work in logistics",
            "I coordinate shipments and talk to suppliers every day",
            "I want to use English at work, mostly in meetings",
            "If I could speak better I would lead those calls myself",
        };
        for (int i = 0; i < dichos.length; i++) {
            comoLead(HttpMethod.POST, "/api/v1/assessments/" + id + "/turns", llave,
                    Map.of("turnIndex", i, "speaker", "USER", "transcript", dichos[i],
                            "latencyMs", 500, "durationMs", 4000));
        }
        return id;
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin cuenta: nombre y dos casillas, y el diagnóstico se hace de principio a fin")
    void seHaceSinCuenta() {
        String llave = nuevoLead("Eduardo");
        assertThat(comoLead(HttpMethod.GET, "/api/v1/assessment-leads/me", llave, null).getBody())
                .containsEntry("firstName", "Eduardo");

        String id = conversacionCompleta(llave);
        ResponseEntity<Map> cerrada = comoLead(HttpMethod.POST,
                "/api/v1/assessments/" + id + "/complete", llave, Map.of());

        assertThat(cerrada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cerrada.getBody()).containsEntry("status", "COMPLETED");
        assertThat(cerrada.getBody().get("score")).isNotNull();
        assertThat((String) cerrada.getBody().get("summary")).contains("Eduardo");
        assertThat(jdbc.queryForObject(
                "select user_id is null from confidence_assessments where id = ?::uuid",
                Boolean.class, id)).isTrue();
    }

    /**
     * La traducción de lo que dice Meissa es una llamada que se paga: solo para el dueño de un
     * diagnóstico vivo. Otro dispositivo no la obtiene, uno cerrado tampoco, y una «frase» de
     * cuatrocientos caracteres o más no es una frase.
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("La traducción de Meissa: solo para el dueño de un diagnóstico vivo")
    void laTraduccionEsDelDueno() {
        String llave = nuevoLead("Eduardo");
        String id = (String) comoLead(HttpMethod.POST, "/api/v1/assessments", llave,
                Map.of("languageCode", "EN")).getBody().get("assessmentId");
        String ruta = "/api/v1/assessments/" + id + "/translate";

        ResponseEntity<Map> traducida = comoLead(HttpMethod.POST, ruta, llave, Map.of("text", "How's your day going?"));
        assertThat(traducida.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(traducida.getBody()).containsEntry("translation", "[es] How's your day going?");
        // La rama en español no se traduce a sí misma.
        assertThat(comoLead(HttpMethod.POST, ruta, llave, Map.of("text", "Sigamos en español, que así me cuentas mejor."))
                .getBody()).containsEntry("translation", null);

        assertThat(comoLead(HttpMethod.POST, ruta, nuevoLead("Ana"), Map.of("text", "Hi")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(comoLead(HttpMethod.POST, ruta, llave, Map.of("text", "x".repeat(401))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        comoLead(HttpMethod.POST, "/api/v1/assessments/" + id + "/abandon", llave, null);
        assertThat(comoLead(HttpMethod.POST, ruta, llave, Map.of("text", "Bye!")).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /**
     * La traducción se carga al presupuesto del diagnóstico, uno solo por función: gastado el del
     * día, la conversación sigue igual y la línea en español simplemente no aparece.
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin presupuesto del diagnóstico no hay traducción, y la conversación sigue")
    void sinPresupuestoNoSeTraduce() {
        String llave = nuevoLead("Eduardo");
        String id = (String) comoLead(HttpMethod.POST, "/api/v1/assessments", llave,
                Map.of("languageCode", "EN")).getBody().get("assessmentId");
        String tope = jdbc.queryForObject(
                "select value from platform_settings where key = 'assessment_daily_budget_cop'", String.class);
        jdbc.update("update platform_settings set value = '0' where key = 'assessment_daily_budget_cop'");
        try {
            ResponseEntity<Map> r = comoLead(HttpMethod.POST, "/api/v1/assessments/" + id + "/translate", llave,
                    Map.of("text", "How's your day going?"));

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).containsEntry("translation", null);
        } finally {
            jdbc.update("update platform_settings set value = ? where key = 'assessment_daily_budget_cop'", tope);
        }
    }

    /**
     * Cada «empezar» entrega una llave del proveedor que se paga, también al retomar uno abierto.
     * Se carga al presupuesto al abrirla —quien pide llaves y no cierra nada consume igual— y hay
     * un tope por persona para que un solo lead no se gaste el día entero.
     */
    @Test
    @DisplayName("Cada sesión de voz se carga al presupuesto al abrirse, y un lead no puede pedir sin fin")
    void cadaSesionSeCargaYTieneTope() {
        jdbc.update("delete from ai_usage_log");
        String llave = nuevoLead("Eduardo");

        for (int i = 0; i < 10; i++) {
            assertThat(comoLead(HttpMethod.POST, "/api/v1/assessments", llave, Map.of("languageCode", "EN"))
                    .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        assertThat(jdbc.queryForObject(
                "select count(*) from ai_usage_log where voice_seconds is not null and cost_cop > 0",
                Integer.class)).isEqualTo(10);
        assertThat(comoLead(HttpMethod.POST, "/api/v1/assessments", llave, Map.of("languageCode", "EN"))
                .getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin las dos casillas no hay lead: ni la mayoría de edad ni la voz se suponen")
    void sinLasCasillasNoSeEmpieza() {
        ResponseEntity<Map> sinVoz = comoLead(HttpMethod.POST, "/api/v1/assessment-leads", null,
                Map.of("firstName", "Ana", "adult", true, "acceptsVoice", false));
        ResponseEntity<Map> sinEdad = comoLead(HttpMethod.POST, "/api/v1/assessment-leads", null,
                Map.of("firstName", "Ana", "adult", false, "acceptsVoice", true));

        assertThat(sinVoz.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sinEdad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select count(*) from assessment_leads", Integer.class))
                .isZero();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin cuenta ni llave no se empieza; con la llave de otro dispositivo no se ve nada")
    void laLlaveSoloAbreLoSuyo() {
        assertThat(comoLead(HttpMethod.POST, "/api/v1/assessments", null,
                Map.of("languageCode", "EN")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String id = conversacionCompleta(nuevoLead("Eduardo"));
        String otra = nuevoLead("Marta");

        assertThat(comoLead(HttpMethod.GET, "/api/v1/assessments/" + id, otra, null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Al entrar con su cuenta, el diagnóstico y la autorización de voz se mudan a ella")
    void alEntrarSeMudaALaCuenta() {
        String llave = nuevoLead("Eduardo");
        String id = conversacionCompleta(llave);
        comoLead(HttpMethod.POST, "/api/v1/assessments/" + id + "/complete", llave, Map.of());
        Instant autorizo = jdbc.queryForObject(
                "select consent_accepted_at from assessment_leads", Timestamp.class).toInstant();

        User eduardo = createUser("eduardo@orion.test", "Eduardo Ruiz", UserRole.STUDENT);
        Session sesion = login("eduardo@orion.test");

        // La primera petición autenticada que trae la llave es la que muda.
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, "ORION_SESSION=" + sesion.cookie() + "; ORION_LEAD=" + llave);
        ResponseEntity<String> yo = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(h), String.class);

        assertThat(yo.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(c -> c.startsWith("ORION_LEAD=;") && c.contains("Max-Age=0"));
        assertThat(jdbc.queryForObject(
                "select user_id from confidence_assessments where id = ?::uuid", UUID.class, id))
                .isEqualTo(eduardo.getId());
        // La constancia es la de cuando la dio, no la de cuando entró.
        assertThat(jdbc.queryForObject(
                "select accepted_at from voice_consents where user_id = ?", Timestamp.class,
                eduardo.getId()).toInstant().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(autorizo.truncatedTo(ChronoUnit.MILLIS));

        // Y desde ahí es de la cuenta: su historial lo tiene, y la llave ya no abre nada.
        List<?> historial = get("/api/v1/me/assessments", sesion, List.class).getBody();
        assertThat(historial).hasSize(1);
        assertThat(comoLead(HttpMethod.GET, "/api/v1/assessments/" + id, llave, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Al crear la cuenta, el navegador lanza varias peticiones a la vez con la misma llave. Solo una
     * muda: antes cada una mudaba lo mismo y dejaba su propia autorización de voz.
     */
    @Test
    @DisplayName("Varias peticiones a la vez con la llave: la mudanza ocurre una sola vez")
    void laMudanzaEsUnaSola() throws Exception {
        String llave = nuevoLead("Eduardo");
        String id = conversacionCompleta(llave);
        comoLead(HttpMethod.POST, "/api/v1/assessments/" + id + "/complete", llave, Map.of());
        User eduardo = createUser("eduardo@orion.test", "Eduardo Ruiz", UserRole.STUDENT);
        Session sesion = login("eduardo@orion.test");
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, "ORION_SESSION=" + sesion.cookie() + "; ORION_LEAD=" + llave);

        int n = 6;
        ExecutorService hilos = Executors.newFixedThreadPool(n);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<HttpStatusCode>> respuestas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            respuestas.add(hilos.submit(() -> {
                salida.await();
                return rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(h), String.class)
                        .getStatusCode();
            }));
        }
        salida.countDown();
        for (Future<HttpStatusCode> r : respuestas) {
            assertThat(r.get(20, TimeUnit.SECONDS)).isEqualTo(HttpStatus.OK);
        }
        hilos.shutdown();

        assertThat(Mudanzas.CUENTAS.stream().filter(eduardo.getId()::equals)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from voice_consents where user_id = ?", Integer.class,
                eduardo.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select user_id from confidence_assessments where id = ?::uuid", UUID.class, id))
                .isEqualTo(eduardo.getId());
        assertThat(jdbc.queryForObject("select claimed_by from assessment_leads", UUID.class))
                .isEqualTo(eduardo.getId());
    }

    @Test
    @DisplayName("Lo que nadie reclama se borra al plazo, con su conversación; lo reclamado se queda")
    void loQueNadieReclamaSeBorra() {
        conversacionCompleta(nuevoLead("Viejo"));
        conversacionCompleta(nuevoLead("Nuevo"));
        jdbc.update("update assessment_leads set created_at = now() - interval '31 days' "
                + "where first_name = 'Viejo'");

        // Por run(), que es lo que llama el programador de tareas de madrugada.
        retencion.run();

        assertThat(jdbc.queryForList("select first_name from assessment_leads", String.class))
                .containsExactly("Nuevo");
        assertThat(jdbc.queryForObject("select count(*) from confidence_assessments", Integer.class))
                .isEqualTo(1);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Con sesión de estudiante no se crea lead: el diagnóstico va a la cuenta")
    void conCuentaNoHayLead() {
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        Session ana = login("ana@orion.test");

        ResponseEntity<Map> r = post("/api/v1/assessment-leads", ana,
                Map.of("firstName", "Ana", "adult", true, "acceptsVoice", true), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
