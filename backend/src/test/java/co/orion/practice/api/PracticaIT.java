package co.orion.practice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.practice.application.PracticeService;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * La práctica entre clases (brief del Bloque 10, Parte B y pasos C2 de la práctica): nace del acta
 * publicada, se genera diferida, se ofrece solo si trae al menos dos ejercicios anclados, solo su
 * dueño la opera, los intentos tienen tope, completarla da los puntos una vez y cuenta para la racha,
 * y el profesor ve un resumen agregado y nunca las respuestas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class PracticaIT extends ApiIntegrationSupport {

    /** Con dos términos entre comillas: el redactor sin IA los vuelve vocabulario. */
    private static final String NOTAS =
            "Trabajamos past simple; sigue diciendo 'I go yesterday' y le costó 'used to'. Próxima: condicionales.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private PracticeService practica;

    private User maria;
    private User ana;
    private Session sesionMaria;
    private Session sesionAna;

    @BeforeEach
    void seed() {
        limpiar();
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana = createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
        createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        sesionMaria = login("maria@orion.test");
        sesionAna = login("ana@orion.test");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("update platform_settings set value = 'true' where key = 'practice_enabled'");
        jdbc.update("delete from point_events where source_type = 'PRACTICE'");
        jdbc.update("delete from practice_sets");
        jdbc.update("delete from lesson_notes");
        jdbc.update("delete from attendance_records");
        bookings.deleteAll();
    }

    /** María cierra una clase con Ana, escribe el acta con esas notas y la publica. */
    @SuppressWarnings("rawtypes")
    private UUID actaPublicada(String notas) {
        UUID clase = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId())).getId();
        jdbc.update("update bookings set status = 'COMPLETED', completed_at = now() where id = ?", clase);
        Map borrador = post("/api/v1/bookings/" + clase + "/lesson-note/draft", sesionMaria,
                Map.of("rawInput", notas), Map.class).getBody();
        post("/api/v1/lesson-notes/" + borrador.get("id") + "/publish", sesionMaria, null, Map.class);
        return clase;
    }

    /** Cierra cada ejercicio con dos respuestas que no son: lo mínimo para poder terminar el set. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void cerrarTodos(Map set) {
        for (Map item : (List<Map>) set.get("items")) {
            for (int intento = 0; intento < 2; intento++) {
                post("/api/v1/practice-items/" + item.get("id") + "/answer", sesionAna, Map.of("answer", "no sé"), Map.class);
            }
        }
    }

    /** El set de Ana, ya generado. */
    @SuppressWarnings("rawtypes")
    private Map setListo() {
        actaPublicada(NOTAS);
        await().atMost(Duration.ofSeconds(5)).until(() ->
                jdbc.queryForObject("select count(*) from practice_sets", Integer.class) == 1);
        practica.generarPendientes();
        return get("/api/v1/me/practice", sesionAna, Map.class).getBody();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Publicar el acta encola el set; el trabajo lo deja listo con cuatro ejercicios anclados")
    void delActaALaPractica() {
        Map set = setListo();

        assertThat(set).containsEntry("status", "READY").containsEntry("itemCount", 4)
                .containsEntry("professorName", "María Gómez");
        List<Map> items = (List<Map>) set.get("items");
        assertThat(items).hasSize(4);
        // Abierto, el ejercicio no trae la respuesta esperada ni la explicación.
        assertThat(items).allSatisfy(i -> assertThat(i).containsEntry("expected", null).containsEntry("explanation", null));
        assertThat(items).extracting(i -> i.get("type")).contains("FILL_BLANK", "WRITE_SENTENCE");
    }

    @Test
    @DisplayName("Un acta sin vocabulario no da práctica: el set falla y al estudiante no se le ofrece nada")
    void sinAnclaNoHayPractica() {
        actaPublicada("Conversación libre sobre viajes, muy tranquila, sin temas nuevos.");
        await().atMost(Duration.ofSeconds(5)).until(() ->
                jdbc.queryForObject("select count(*) from practice_sets", Integer.class) == 1);

        for (int i = 0; i < 3; i++) {
            practica.generarPendientes();
        }

        assertThat(jdbc.queryForObject("select status from practice_sets", String.class)).isEqualTo("FAILED");
        assertThat(get("/api/v1/me/practice", sesionAna, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Dos intentos por ejercicio; el tercero es 422 y la respuesta ya se mostró")
    void intentos() {
        Map set = setListo();
        post("/api/v1/practice-sets/" + set.get("id") + "/start", sesionAna, null, Map.class);
        Map hueco = ((List<Map>) set.get("items")).stream().filter(i -> "FILL_BLANK".equals(i.get("type")))
                .findFirst().orElseThrow();
        String ruta = "/api/v1/practice-items/" + hueco.get("id") + "/answer";

        Map primero = post(ruta, sesionAna, Map.of("answer", "nada que ver"), Map.class).getBody();
        assertThat(primero).containsEntry("correct", false).containsEntry("closed", false).containsEntry("attemptsLeft", 1);
        Map segundo = post(ruta, sesionAna, Map.of("answer", "tampoco"), Map.class).getBody();
        assertThat(segundo).containsEntry("closed", true);
        assertThat((Map) segundo.get("item")).containsKey("expected").extractingByKey("expected").isNotNull();

        assertThat(post(ruta, sesionAna, Map.of("answer", "otra vez"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Una frase propia válida que nadie previó es correcta")
    void fraseImprevista() {
        Map set = setListo();
        Map escribir = ((List<Map>) set.get("items")).stream().filter(i -> "WRITE_SENTENCE".equals(i.get("type")))
                .filter(i -> ((String) i.get("payload")).contains("used to")).findFirst().orElseThrow();

        Map r = post("/api/v1/practice-items/" + escribir.get("id") + "/answer", sesionAna,
                Map.of("answer", "When I was a kid I used to climb trees with my brother."), Map.class).getBody();

        assertThat(r).containsEntry("correct", true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Corregir la frase: las otras correcciones válidas no viajan mientras el ejercicio está abierto")
    void corregirNoAdelantaLasRespuestas() {
        Map set = setListo();
        Object id = ((List<Map>) set.get("items")).getFirst().get("id");
        jdbc.update("update practice_items set item_type = 'FIX_SENTENCE', expected = 'I went yesterday', "
                + "payload = '{\"sentence\":\"I go yesterday\",\"accepted\":[\"Yesterday I went\"]}'::jsonb "
                + "where id = ?::uuid", id);

        Map abierto = ((List<Map>) get("/api/v1/practice-sets/" + set.get("id"), sesionAna, Map.class).getBody()
                .get("items")).getFirst();
        assertThat((String) abierto.get("payload")).contains("I go yesterday").doesNotContain("accepted");

        post("/api/v1/practice-items/" + id + "/answer", sesionAna, Map.of("answer", "I goed yesterday"), Map.class);
        Map cerrado = (Map) post("/api/v1/practice-items/" + id + "/answer", sesionAna,
                Map.of("answer", "I gone yesterday"), Map.class).getBody().get("item");
        assertThat((String) cerrado.get("payload")).contains("Yesterday I went");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Solo su dueño: responder el ejercicio de otro estudiante es 404")
    void soloElDueno() {
        Map set = setListo();
        Object item = ((List<Map>) set.get("items")).getFirst().get("id");

        assertThat(post("/api/v1/practice-items/" + item + "/answer", login("carlos@orion.test"),
                Map.of("answer", "used to"), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/practice-sets/" + set.get("id"), login("carlos@orion.test"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Un set vencido no acepta respuestas")
    void vencido() {
        Map set = setListo();
        jdbc.update("update practice_sets set expires_at = now() - interval '1 minute'");
        Object item = ((List<Map>) set.get("items")).getFirst().get("id");

        assertThat(post("/api/v1/practice-items/" + item + "/answer", sesionAna, Map.of("answer", "used to"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(practica.expirarVencidos()).isEqualTo(1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Completar dos veces da los puntos una sola vez, y la práctica cuenta para la racha")
    void completarYRacha() {
        Map set = setListo();
        String ruta = "/api/v1/practice-sets/" + set.get("id") + "/complete";
        // Sin responder no se termina: los puntos y la semana de racha son por practicar.
        assertThat(post(ruta, sesionAna, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        cerrarTodos(set);

        assertThat(post(ruta, sesionAna, null, Map.class).getBody()).containsEntry("status", "COMPLETED");
        assertThat(post(ruta, sesionAna, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from point_events where source_type = 'PRACTICE' and user_id = ?",
                Integer.class, ana.getId())).isEqualTo(1));
        // Y quien lo garantiza es la base, no el servicio (brief, B4 y C2): un segundo punto por el
        // mismo set no entra ni escrito a mano.
        assertThatThrownBy(() -> jdbc.update("""
                insert into point_events (user_id, source_type, source_id, points, occurred_at)
                values (?, 'PRACTICE', ?::uuid, 15, now())""", ana.getId(), set.get("id")))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Ana no tiene ninguna clase tomada en su historial de puntos: la semana activa es la práctica.
        assertThat((Integer) get("/api/v1/me/engagement", sesionAna, Map.class).getBody().get("currentStreakWeeks"))
                .isGreaterThanOrEqualTo(1);
        // Y el mapa de constancia dice lo mismo: esta semana está cumplida, con solo la práctica.
        List<Map> semanas = (List<Map>) get("/api/v1/me/streak?weeks=1", sesionAna, Map.class).getBody().get("weeks");
        assertThat(semanas).singleElement().satisfies(w -> assertThat(w).containsEntry("status", "CUMPLIDA"));
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El profesor ve cuánto practicó y dónde le costó, nunca las respuestas; otro profesor, nada")
    void loQueVeElProfesor() {
        Map set = setListo();
        cerrarTodos(set);
        post("/api/v1/practice-sets/" + set.get("id") + "/complete", sesionAna, null, Map.class);

        Map resumen = get("/api/v1/professors/me/students/" + ana.getId() + "/practice", sesionMaria, Map.class).getBody();
        assertThat(resumen).containsOnlyKeys("ofrecidasEstaSemana", "completadasEstaSemana", "leCosto")
                .containsEntry("ofrecidasEstaSemana", 1).containsEntry("completadasEstaSemana", 1);

        assertThat(get("/api/v1/professors/me/students/" + ana.getId() + "/practice", login("juan@orion.test"),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El panel del admin cuenta sets generados, completados y vencidos; un estudiante no lo ve")
    void elPanelDelAdmin() {
        Map set = setListo();
        cerrarTodos(set);
        post("/api/v1/practice-sets/" + set.get("id") + "/complete", sesionAna, null, Map.class);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        Map panel = get("/api/v1/admin/practice/metrics", login("admin@orion.test"), Map.class).getBody();

        assertThat(panel).containsEntry("generados", 1).containsEntry("completados", 1)
                .containsEntry("vencidos", 0).containsEntry("encendida", true);
        assertThat(get("/api/v1/admin/practice/metrics", sesionAna, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Con la práctica apagada, el acta se publica igual y no se crea ningún set")
    void apagada() {
        jdbc.update("update platform_settings set value = 'false' where key = 'practice_enabled'");

        actaPublicada(NOTAS);

        assertThat(jdbc.queryForObject("select count(*) from lesson_notes where status = 'PUBLISHED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from practice_sets", Integer.class)).isZero();
        assertThat(get("/api/v1/me/practice", sesionAna, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
