package co.orion.teaching.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;
import co.orion.teaching.application.LessonNoteNudgeJob;
import co.orion.teaching.application.TeachingAiBudget;

/**
 * El acta de clase (Bloque 10, Parte A), con los casos que exige el paso C2 del brief: quién puede
 * qué, que el borrador no exista para el estudiante, que publicar avise una vez, la ventana de
 * edición, el camino a mano cuando no hay IA y el recordatorio que no insiste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class LessonNoteIT extends ApiIntegrationSupport {

    private static final String NOTAS =
            "Trabajamos past simple; sigue diciendo 'I go yesterday' y le costó 'used to'. Próxima: condicionales.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private LessonNoteNudgeJob recordatorio;

    @Autowired
    private TeachingAiBudget presupuesto;

    private User maria;
    private User ana;
    private Session sesionMaria;
    private Session sesionAna;

    @BeforeEach
    void seed() {
        jdbc.update("delete from ai_usage_log where feature = 'lesson_note'");
        jdbc.update("delete from lesson_vocabulary");
        jdbc.update("delete from lesson_notes");
        jdbc.update("delete from attendance_records");
        bookings.deleteAll();
        users.deleteAll();
        jdbc.update("update platform_settings set value = 'true' where key = 'ai_lesson_notes_enabled'");
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana = createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
        createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        createUser("carlos@orion.test", "Carlos Peña", UserRole.STUDENT);
        sesionMaria = login("maria@orion.test");
        sesionAna = login("ana@orion.test");
    }

    /**
     * Deja la base como la encontró: las clases de aquí apuntan a usuarios, y las pruebas que
     * vienen después borran usuarios sin borrar antes las reservas (las actas caen en cascada).
     */
    @AfterEach
    void restaurar() {
        jdbc.update("update platform_settings set value = 'true' where key = 'ai_lesson_notes_enabled'");
        jdbc.update("update platform_settings set value = '60' where key = 'lesson_note_nudge_minutes'");
        jdbc.update("update platform_settings set value = '30000' where key = 'ai_daily_budget_cop'");
        jdbc.update("delete from attendance_records");
        bookings.deleteAll();
    }

    /** Una clase de María con Ana que ya se dictó y se cerró ahora (después de que existieran las actas). */
    private UUID claseDictada() {
        Booking b = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId()));
        jdbc.update("update bookings set status = 'COMPLETED', completed_at = now() where id = ?", b.getId());
        return b.getId();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> redactar(Session sesion, UUID clase, String notas) {
        return post("/api/v1/bookings/" + clase + "/lesson-note/draft", sesion, Map.of("rawInput", notas), Map.class);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> publicar(Session sesion, Object notaId) {
        return post("/api/v1/lesson-notes/" + notaId + "/publish", sesion, null, Map.class);
    }

    private int avisos(UUID usuario, String tipo) {
        return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = ?",
                Integer.class, usuario, tipo);
    }

    @Test
    @DisplayName("Sin clase dictada no hay acta: 422 con un mensaje claro")
    void sinClaseDictadaNoHayActa() {
        Booking futura = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId()));

        assertThat(redactar(sesionMaria, futura.getId(), NOTAS).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("Las clases cerradas antes de que existieran las actas no admiten una")
    void sinActasRetroactivas() {
        UUID clase = claseDictada();
        jdbc.update("update bookings set completed_at = '2020-01-01T00:00:00Z' where id = ?", clase);

        assertThat(redactar(sesionMaria, clase, NOTAS).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("Solo el profesor de la clase escribe el acta: otro profesor 403, un tercero no la ve (404)")
    void quienPuedeQue() {
        UUID clase = claseDictada();

        assertThat(redactar(login("juan@orion.test"), clase, NOTAS).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        redactar(sesionMaria, clase, NOTAS);
        assertThat(get("/api/v1/bookings/" + clase + "/lesson-note", login("carlos@orion.test"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // El estudiante no escribe actas: ni la ruta del profesor le abre.
        assertThat(redactar(sesionAna, clase, NOTAS).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Menos de 20 caracteres no es nada que ordenar: 400")
    void minimoVeinteCaracteres() {
        assertThat(redactar(sesionMaria, claseDictada(), "corto").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("El borrador no existe para el estudiante; publicada, la lee sin el cuaderno del profesor")
    void elBorradorEsInvisibleYLaPublicadaNoLlevaElCrudo() {
        UUID clase = claseDictada();
        Map borrador = redactar(sesionMaria, clase, NOTAS).getBody();
        assertThat(borrador).containsEntry("status", "DRAFT").containsEntry("origin", "AI_DRAFT");
        assertThat((List<Map>) borrador.get("vocabulary")).extracting(p -> p.get("term"))
                .contains("I go yesterday", "used to");

        assertThat(get("/api/v1/bookings/" + clase + "/lesson-note", sesionAna, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(publicar(sesionMaria, borrador.get("id")).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map deAna = get("/api/v1/bookings/" + clase + "/lesson-note", sesionAna, Map.class).getBody();
        assertThat(deAna).containsKey("workedOn").containsEntry("professorName", "María Gómez");
        assertThat(deAna).doesNotContainKeys("rawInput", "editRatio", "origin", "promptVersion", "draftedByAi");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Publicar dos veces no cambia nada ni avisa dos veces")
    void publicarEsIdempotente() {
        Map borrador = redactar(sesionMaria, claseDictada(), NOTAS).getBody();

        publicar(sesionMaria, borrador.get("id"));
        assertThat(publicar(sesionMaria, borrador.get("id")).getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(avisos(ana.getId(), "LESSON_NOTE_PUBLISHED")).isEqualTo(1));
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("edit_ratio: publicada tal cual es 0; reescrita entera, cerca de 1")
    void cuantoSeCorrigio() {
        Map tal = redactar(sesionMaria, claseDictada(), NOTAS).getBody();
        publicar(sesionMaria, tal.get("id"));

        Map otra = redactar(sesionMaria, claseDictada(), NOTAS).getBody();
        put("/api/v1/lesson-notes/" + otra.get("id"), sesionMaria, Map.of(
                "workedOn", "Conversación libre sobre viajes, sin gramática formal.",
                "recurringIssues", "Pronunciación de la th.",
                "nextSteps", "Leer un artículo corto.",
                "vocabulary", List.of(Map.of("term", "luggage"))), Map.class);
        publicar(sesionMaria, otra.get("id"));

        assertThat(jdbc.queryForObject("select edit_ratio from lesson_notes where id = ?::uuid",
                Double.class, tal.get("id"))).isZero();
        assertThat(jdbc.queryForObject("select edit_ratio from lesson_notes where id = ?::uuid",
                Double.class, otra.get("id"))).isGreaterThan(0.6);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Publicada, se edita dentro de las 72 horas: a las 71 sí, a las 73 no")
    void ventanaDeEdicion() {
        Map borrador = redactar(sesionMaria, claseDictada(), NOTAS).getBody();
        publicar(sesionMaria, borrador.get("id"));
        Map cambio = Map.of("workedOn", "Past simple y used to.");

        jdbc.update("update lesson_notes set published_at = now() - interval '71 hours' where id = ?::uuid",
                borrador.get("id"));
        ResponseEntity<Map> a71 = put("/api/v1/lesson-notes/" + borrador.get("id"), sesionMaria, cambio, Map.class);
        assertThat(a71.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(a71.getBody().get("lastEditedAt")).isNotNull();

        jdbc.update("update lesson_notes set published_at = now() - interval '73 hours' where id = ?::uuid",
                borrador.get("id"));
        assertThat(put("/api/v1/lesson-notes/" + borrador.get("id"), sesionMaria, cambio, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Con la IA apagada no hay error: los mismos campos, vacíos, y las notas guardadas")
    void sinIaElCaminoAMano() {
        jdbc.update("update platform_settings set value = 'false' where key = 'ai_lesson_notes_enabled'");

        ResponseEntity<Map> r = redactar(sesionMaria, claseDictada(), NOTAS);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("origin", "MANUAL").containsEntry("draftedByAi", false)
                .containsEntry("rawInput", NOTAS);
        assertThat(r.getBody().get("workedOn")).isNull();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("El resumen para la lista: el profesor ve sus borradores; el estudiante, solo lo publicado")
    void resumenParaLaLista() {
        UUID clase = claseDictada();
        UUID antigua = claseDictada();
        jdbc.update("update bookings set completed_at = '2020-01-01T00:00:00Z' where id = ?", antigua);
        // Cerrada y sin acta: el profesor la ve pendiente; la de antes de las actas, no.
        Map pendientes = (Map) get("/api/v1/me/lesson-notes/summary", sesionMaria, Map.class).getBody()
                .get("byBooking");
        assertThat(pendientes).containsEntry(clase.toString(), "PENDING").doesNotContainKey(antigua.toString());

        Map borrador = redactar(sesionMaria, clase, NOTAS).getBody();

        Map deMaria = get("/api/v1/me/lesson-notes/summary", sesionMaria, Map.class).getBody();
        assertThat((Map) deMaria.get("byBooking")).containsEntry(clase.toString(), "DRAFT");
        assertThat(deMaria.get("since")).isNotNull();
        assertThat((Map) get("/api/v1/me/lesson-notes/summary", sesionAna, Map.class).getBody().get("byBooking"))
                .isEmpty();

        publicar(sesionMaria, borrador.get("id"));
        assertThat((Map) get("/api/v1/me/lesson-notes/summary", sesionAna, Map.class).getBody().get("byBooking"))
                .containsEntry(clase.toString(), "PUBLISHED");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("La lista: al profesor, lo que le falta; al estudiante, lo publicado, con quién y cuándo")
    void laListaDeActas() {
        UUID pendiente = claseDictada();
        assertThat(get("/api/v1/me/lesson-notes/index", sesionMaria, List.class).getBody())
                .singleElement().satisfies(e -> assertThat((Map) e)
                        .containsEntry("bookingId", pendiente.toString())
                        .containsEntry("status", "PENDING")
                        .containsEntry("counterpartName", "Ana Ruiz"));
        assertThat(get("/api/v1/me/lesson-notes/index", sesionAna, List.class).getBody()).isEmpty();

        Map borrador = redactar(sesionMaria, pendiente, NOTAS).getBody();
        publicar(sesionMaria, borrador.get("id"));

        // Publicada, deja de faltarle al profesor y aparece para el estudiante.
        assertThat(get("/api/v1/me/lesson-notes/index", sesionMaria, List.class).getBody()).isEmpty();
        assertThat(get("/api/v1/me/lesson-notes/index", sesionAna, List.class).getBody())
                .singleElement().satisfies(e -> assertThat((Map) e)
                        .containsEntry("counterpartName", "María Gómez")
                        .containsEntry("status", "PUBLISHED")
                        .containsKey("publishedAt"));
    }

    /**
     * Toda llamada al proveedor deja su fila en el registro de gasto, termine como termine (brief,
     * A2 y C2), y con el tope del día gastado la IA se apaga: el profesor ve el camino a mano, que
     * es lo que prueba {@link #sinIaElCaminoAMano()}.
     */
    @Test
    @DisplayName("Cada llamada deja su fila, también un TIMEOUT; con el tope gastado, la IA se apaga")
    void registroYTope() {
        presupuesto.registrar(maria.getId(), "gpt-5-mini", null, null, 25_000, "TIMEOUT");
        assertThat(jdbc.queryForObject(
                "select outcome from ai_usage_log where feature = 'lesson_note' and actor_id = ?",
                String.class, maria.getId())).isEqualTo("TIMEOUT");
        assertThat(presupuesto.disponible()).isTrue();

        jdbc.update("update platform_settings set value = '10' where key = 'ai_daily_budget_cop'");
        presupuesto.registrar(maria.getId(), "gpt-5-mini", 20_000, 4_000, 3_000, "OK");

        assertThat(presupuesto.gastadoHoy()).isGreaterThanOrEqualTo(10);
        assertThat(presupuesto.disponible()).isFalse();
    }

    /**
     * Por {@code run()}, que es lo que llama el programador de tareas: antes la transacción vivía
     * solo en {@code recordar()} y la llamada interna se saltaba el proxy, así que la clase quedaba
     * marcada como recordada y el aviso —que se publica después del commit— se perdía para siempre.
     */
    @Test
    @DisplayName("El recordatorio al profesor sale una vez y nunca insiste")
    void elRecordatorioNoInsiste() {
        jdbc.update("update platform_settings set value = '0' where key = 'lesson_note_nudge_minutes'");
        claseDictada();

        recordatorio.run();
        assertThat(recordatorio.recordar()).isZero();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(avisos(maria.getId(), "LESSON_NOTE_NUDGE")).isEqualTo(1));
    }
}
