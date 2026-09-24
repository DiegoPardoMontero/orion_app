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
        jdbc.update("delete from point_events where source_type in ('PRACTICE', 'PRACTICE_PERFECT')");
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
    @DisplayName("Mientras se prepara, la práctica ya se ofrece como «preparando», sin ejercicios")
    void preparandose() {
        actaPublicada(NOTAS);
        await().atMost(Duration.ofSeconds(5)).until(() ->
                jdbc.queryForObject("select count(*) from practice_sets", Integer.class) == 1);

        Map set = get("/api/v1/me/practice", sesionAna, Map.class).getBody();

        assertThat(set).containsEntry("status", "PENDING").containsEntry("professorName", "María Gómez");
        assertThat((List) set.get("items")).isEmpty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Publicar el acta encola el set; el trabajo lo deja listo con ejercicios anclados, uno de cada tipo")
    void delActaALaPractica() {
        Map set = setListo();

        assertThat(set).containsEntry("status", "READY").containsEntry("itemCount", 3)
                .containsEntry("professorName", "María Gómez");
        List<Map> items = (List<Map>) set.get("items");
        assertThat(items).hasSize(3);
        // Abierto, el ejercicio no trae la respuesta esperada ni la explicación.
        assertThat(items).allSatisfy(i -> assertThat(i).containsEntry("expected", null).containsEntry("explanation", null));
        // Sin IA: un ejercicio de cada tipo que el acta alcanza, en el orden de las categorías.
        assertThat(items).extracting(i -> i.get("type")).containsExactly("FILL_BLANK", "DICTATION", "WRITE_SENTENCE");
        assertThat(items).extracting(i -> i.get("category")).containsExactly("PALABRAS", "ESCUCHA", "TU_TURNO");
    }

    @Test
    @DisplayName("D7: el set guarda el nivel y el objetivo de la ficha del estudiante, como estaban al publicar")
    void elSetLlevaNivelYObjetivo() {
        jdbc.update("update student_profiles set self_declared_level = 'ADVANCED', motivation = ? where user_id = ?",
                "Presentaciones\nen el trabajo", ana.getId());

        actaPublicada(NOTAS);
        await().atMost(Duration.ofSeconds(5)).until(() ->
                jdbc.queryForObject("select count(*) from practice_sets", Integer.class) == 1);

        assertThat(jdbc.queryForObject("select material->>'studentLevel' from practice_sets", String.class))
                .isEqualTo("ADVANCED");
        assertThat(jdbc.queryForObject("select material->>'studentGoal' from practice_sets", String.class))
                .isEqualTo("Presentaciones en el trabajo");
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
        assertThat(resumen).containsOnlyKeys("ofrecidasEstaSemana", "completadasEstaSemana", "ofrecidasEsteMes",
                        "completadasEsteMes", "leCosto")
                .containsEntry("ofrecidasEstaSemana", 1).containsEntry("completadasEstaSemana", 1)
                .containsEntry("ofrecidasEsteMes", 1).containsEntry("completadasEsteMes", 1);

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("El profesor ve cada ejercicio de su acta con lo que respondió su estudiante en cada intento")
    void elProfesorVeLoQueHizoSuEstudiante() {
        Map set = setListo();
        cerrarTodos(set);
        String ruta = "/api/v1/professors/me/lesson-notes/" + set.get("lessonNoteId") + "/practice";

        Map vista = get(ruta, sesionMaria, Map.class).getBody();

        assertThat(vista).containsEntry("status", "IN_PROGRESS").containsEntry("itemCount", 3)
                .containsEntry("studentName", "Ana Ruiz").containsEntry("firstTry", 0).containsEntry("shown", 3);
        List<Map> items = (List<Map>) vista.get("items");
        assertThat(items).hasSize(3).allSatisfy(i -> {
            assertThat(i).containsEntry("firstAnswer", "no sé").containsEntry("secondAnswer", "no sé")
                    .containsEntry("attempts", 2).containsEntry("correct", false).containsEntry("skipped", false);
            assertThat(i.get("explanation")).isNotNull();
            assertThat(i.get("category")).isNotNull();
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("En la ficha, el profesor ve el historial de prácticas de su estudiante; otro profesor, nada")
    void elHistorialEnLaFicha() {
        Map set = setListo();
        cerrarTodos(set);
        post("/api/v1/practice-sets/" + set.get("id") + "/complete", sesionAna, null, Map.class);
        String ruta = "/api/v1/professors/me/students/" + ana.getId() + "/practice-sets";

        List<Map> historial = get(ruta, sesionMaria, List.class).getBody();

        assertThat(historial).singleElement().satisfies(h -> {
            assertThat(h).containsEntry("status", "COMPLETED").containsEntry("itemCount", 3)
                    .containsEntry("lessonNoteId", set.get("lessonNoteId"));
            assertThat(h.get("bookingId")).isNotNull();
            assertThat(h.get("workedOn")).isNotNull();
            // Todo cerrado con dos respuestas que no eran: tres estrellas mostradas, para su constelación.
            assertThat((List) h.get("stars")).containsExactly("mostrada", "mostrada", "mostrada");
        });
        assertThat(get(ruta, login("juan@orion.test"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Los ejercicios de un acta no los ve otro profesor ni el estudiante; sin práctica, 204")
    void losEjerciciosDelActaSonDeSuProfesor() {
        Map set = setListo();
        String ruta = "/api/v1/professors/me/lesson-notes/" + set.get("lessonNoteId") + "/practice";

        assertThat(get(ruta, login("juan@orion.test"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(ruta, sesionAna, String.class).getStatusCode())
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/professors/me/lesson-notes/" + UUID.randomUUID() + "/practice", sesionMaria,
                String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Sin voz en inglés, un ejercicio de escucha se salta: queda cerrado sin contar como error")
    void saltarLaEscucha() {
        Map set = setListo();
        List<Map> items = (List<Map>) set.get("items");
        Map escucha = items.stream().filter(i -> "DICTATION".equals(i.get("type"))).findFirst().orElseThrow();
        Map hueco = items.stream().filter(i -> "FILL_BLANK".equals(i.get("type"))).findFirst().orElseThrow();

        ResponseEntity<Map> saltado = post("/api/v1/practice-items/" + escucha.get("id") + "/skip", sesionAna, null, Map.class);

        assertThat(saltado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saltado.getBody()).containsEntry("closed", true).containsEntry("skipped", true)
                .containsEntry("correct", null);
        assertThat(saltado.getBody().get("explanation")).isNotNull();
        // Solo se saltan los de escucha, y lo saltado ya no se responde.
        assertThat(post("/api/v1/practice-items/" + hueco.get("id") + "/skip", sesionAna, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(post("/api/v1/practice-items/" + escucha.get("id") + "/answer", sesionAna,
                Map.of("answer", "used to"), Map.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        // Con el resto cerrado, el set se completa; lo saltado no es acierto ni fallo.
        for (Map item : items) {
            if (item != escucha) {
                for (int intento = 0; intento < 2; intento++) {
                    post("/api/v1/practice-items/" + item.get("id") + "/answer", sesionAna, Map.of("answer", "no sé"), Map.class);
                }
            }
        }
        assertThat(post("/api/v1/practice-sets/" + set.get("id") + "/complete", sesionAna, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select is_correct is null and skipped_at is not null from practice_items where id = ?",
                Boolean.class, UUID.fromString((String) escucha.get("id")))).isTrue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Una constelación perfecta da 15 + 5 puntos y enciende sus logros de práctica")
    void constelacionPerfecta() {
        Map set = setListo();
        post("/api/v1/practice-sets/" + set.get("id") + "/start", sesionAna, null, Map.class);
        for (Map item : (List<Map>) set.get("items")) {
            String esperada = jdbc.queryForObject("select expected from practice_items where id = ?", String.class,
                    UUID.fromString((String) item.get("id")));
            String respuesta = "WRITE_SENTENCE".equals(item.get("type"))
                    ? "When I was a kid I used to play football every day." : esperada;
            Map r = post("/api/v1/practice-items/" + item.get("id") + "/answer", sesionAna,
                    Map.of("answer", respuesta), Map.class).getBody();
            assertThat(r).containsEntry("correct", true);
            // Cerrado, acertado también trae lo esperado: en «caza el error» es la frase bien dicha.
            assertThat((Map) r.get("item")).containsEntry("expected", esperada);
        }

        post("/api/v1/practice-sets/" + set.get("id") + "/complete", sesionAna, null, Map.class);
        List<Map> historial = get("/api/v1/me/practice/history", sesionAna, List.class).getBody();
        assertThat(historial).hasSize(1);
        assertThat((List) historial.getFirst().get("items")).hasSize(((List) set.get("items")).size());

        assertThat(jdbc.queryForObject("""
                select sum(points) from point_events
                where user_id = ? and source_type in ('PRACTICE', 'PRACTICE_PERFECT')""", Integer.class, ana.getId()))
                .isEqualTo(20);
        assertThat(jdbc.queryForList("""
                select achievement_code from user_achievements where user_id = ? and unlocked_at is not null""",
                String.class, ana.getId())).contains("practica-primera", "practica-perfecta");
        assertThat(jdbc.queryForObject("select perfect from practice_tallies where practice_set_id = ?", Boolean.class,
                UUID.fromString((String) set.get("id")))).isTrue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Con un fallo ya no es perfecta: solo los 15 de siempre")
    void conUnFalloNoEsPerfecta() {
        Map set = setListo();
        cerrarTodos(set);
        post("/api/v1/practice-sets/" + set.get("id") + "/complete", sesionAna, null, Map.class);

        assertThat(jdbc.queryForObject("""
                select coalesce(sum(points), 0) from point_events
                where user_id = ? and source_type = 'PRACTICE_PERFECT'""", Integer.class, ana.getId())).isZero();
        assertThat(jdbc.queryForList("""
                select achievement_code from user_achievements where user_id = ? and unlocked_at is not null""",
                String.class, ana.getId())).contains("practica-primera").doesNotContain("practica-perfecta");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Tras un fallo va la pista, no la explicación; cerrado, la explicación")
    void laPistaYLaExplicacion() {
        Map set = setListo();
        Map hueco = ((List<Map>) set.get("items")).stream().filter(i -> "FILL_BLANK".equals(i.get("type")))
                .findFirst().orElseThrow();
        String ruta = "/api/v1/practice-items/" + hueco.get("id") + "/answer";

        Map casi = (Map) post(ruta, sesionAna, Map.of("answer", "no sé"), Map.class).getBody().get("item");
        assertThat(casi).containsEntry("explanation", null);
        assertThat((String) casi.get("hint")).contains("búscala en tu resumen");

        Map cerrado = (Map) post(ruta, sesionAna, Map.of("answer", "tampoco"), Map.class).getBody().get("item");
        assertThat(cerrado).containsEntry("hint", null);
        assertThat(cerrado.get("explanation")).isNotNull();
        assertThat(cerrado.get("expected")).isNotNull();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Parejas de a una: el par que va se queda unido sin gastar intento; el que no, gasta uno")
    void parejasDeAUna() {
        Map set = setListo();
        UUID setId = UUID.fromString((String) set.get("id"));
        UUID pareja = jdbc.queryForObject("""
                insert into practice_items (practice_set_id, item_index, item_type, prompt, payload, expected, explanation)
                values (?, 9, 'MATCH_MEANING', 'Une.',
                        '{"terms":["used to","deadline","strength"],"meanings":["fecha límite","fortaleza","solía"]}'::jsonb,
                        '{"used to":"solía","deadline":"fecha límite","strength":"fortaleza"}', 'Son las de tu clase.')
                returning id""", UUID.class, setId);
        String ruta = "/api/v1/practice-items/" + pareja + "/pair";

        Map va = post(ruta, sesionAna, Map.of("term", "used to", "meaning", "solía"), Map.class).getBody();
        assertThat(va).containsEntry("pairCorrect", true).containsEntry("closed", false).containsEntry("attemptsLeft", 2);
        Map noVa = post(ruta, sesionAna, Map.of("term", "deadline", "meaning", "fortaleza"), Map.class).getBody();
        assertThat(post(ruta, sesionAna, Map.of("term", "used to", "meaning", "solía"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(noVa).containsEntry("pairCorrect", false).containsEntry("attemptsLeft", 1);
        post(ruta, sesionAna, Map.of("term", "strength", "meaning", "fortaleza"), Map.class);
        Map fin = post(ruta, sesionAna, Map.of("term", "deadline", "meaning", "fecha límite"), Map.class).getBody();

        assertThat(fin).containsEntry("pairCorrect", true).containsEntry("closed", true);
        assertThat((Map) fin.get("item")).containsEntry("correct", true).containsEntry("attempts", 2);
    }
}
