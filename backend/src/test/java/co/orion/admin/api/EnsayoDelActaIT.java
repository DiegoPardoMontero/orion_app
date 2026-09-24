package co.orion.admin.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import co.orion.billing.persistence.PaymentRepository;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.domain.BookingStatus;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;
import co.orion.teaching.application.LessonNoteNudgeJob;

/**
 * El ensayo del acta y la práctica: una clase de prueba que ya se dictó, para recorrer el Bloque 10
 * sin dar una clase de una hora. Como la clase de prueba del aula, lo que se fija es sobre todo lo
 * que no hace: no cobra, no recuerda por correo y no mueve el porcentaje de actas de nadie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class EnsayoDelActaIT extends ApiIntegrationSupport {

    private static final String RUTA = "/api/v1/admin/system/rehearsal";
    private static final String NOTAS =
            "Trabajamos past simple; sigue diciendo 'I go yesterday'. Vocabulario: 'used to' (solía).";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private LessonNoteNudgeJob recordatorio;

    private User maria;
    private Session admin;
    private Session sesionMaria;

    @BeforeEach
    void seed() {
        limpiar();
        users.deleteAll();
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        admin = login("admin@orion.test");
        sesionMaria = login("maria@orion.test");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("update platform_settings set value = '60' where key = 'lesson_note_nudge_minutes'");
        jdbc.update("delete from practice_sets");
        jdbc.update("delete from lesson_notes");
        bookings.deleteAll();
    }

    @SuppressWarnings("rawtypes")
    private UUID ensayar() {
        ResponseEntity<Map> r = post(RUTA, admin,
                Map.of("studentEmail", "ana@orion.test", "professorEmail", "maria@orion.test"), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) r.getBody().get("bookingId"));
    }

    @SuppressWarnings("rawtypes")
    private void escribirYPublicar(UUID clase) {
        ResponseEntity<Map> borrador = post("/api/v1/bookings/" + clase + "/lesson-note/draft", sesionMaria,
                Map.of("rawInput", NOTAS), Map.class);
        assertThat(borrador.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/lesson-notes/" + borrador.getBody().get("id") + "/publish", sesionMaria, null,
                Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map> ensayos() {
        ResponseEntity<Map> r = get("/api/v1/admin/system/rehearsals", admin, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map>) r.getBody().get("ensayos");
    }

    @Test
    @DisplayName("Nace dictada y cerrada, en inglés, marcada como prueba y sin ningún pago")
    void naceDictada() {
        UUID id = ensayar();

        var clase = bookings.findById(id).orElseThrow();
        assertThat(clase.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(clase.getCompletedAt()).isNotNull();
        assertThat(clase.isTrial()).isTrue();
        assertThat(clase.getLanguageCode()).isEqualTo("EN");
        assertThat(payments.findByBookingId(id)).isEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El profesor escribe el acta enseguida, y el estado muestra el acta y la práctica encolada")
    void elRecorridoSeVe() {
        UUID id = ensayar();
        assertThat(ensayos()).singleElement().satisfies(e -> {
            assertThat(e.get("bookingId")).isEqualTo(id.toString());
            assertThat(e.get("acta")).isNull();
            assertThat(e.get("profesorEmail")).isEqualTo("maria@orion.test");
        });

        escribirYPublicar(id);

        Map e = ensayos().getFirst();
        assertThat(e.get("acta")).isEqualTo("PUBLISHED");
        assertThat(e.get("practica")).isNotNull();
        assertThat(e.get("practicaId")).isNotNull();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Dice lo que impediría ver el recorrido: aquí no hay OpenAI")
    void avisaLoQueFalta() {
        ResponseEntity<Map> r = get("/api/v1/admin/system/rehearsals", admin, Map.class);
        assertThat((List<String>) r.getBody().get("avisos")).anyMatch(a -> a.contains("OpenAI"));
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("No mueve el porcentaje de actas del profesor")
    void noCuentaEnElDesempeno() {
        escribirYPublicar(ensayar());

        ResponseEntity<Map> r = get("/api/v1/professors/me/lesson-notes/share", sesionMaria, Map.class);
        assertThat(((Number) r.getBody().get("clasesCerradas")).intValue()).isZero();
        assertThat(((Number) r.getBody().get("clasesConActa")).intValue()).isZero();
    }

    @Test
    @DisplayName("No le manda al profesor el recordatorio de escribir el acta")
    void noRecuerdaPorCorreo() {
        UUID id = ensayar();
        jdbc.update("update platform_settings set value = '0' where key = 'lesson_note_nudge_minutes'");
        jdbc.update("update bookings set completed_at = now() - interval '5 minutes' where id = ?", id);

        recordatorio.recordar();

        assertThat(jdbc.queryForObject("select note_nudge_sent_at is null from bookings where id = ?",
                Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ?",
                Integer.class, maria.getId())).isZero();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Una clase de prueba no se califica: no puede mover la reputación del profesor")
    void noSeCalifica() {
        UUID id = ensayar();
        Session ana = login("ana@orion.test");

        ResponseEntity<Map> r = post("/api/v1/bookings/" + id + "/review", ana,
                Map.of("rating", 5, "comment", "Excelente"), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        // Y la agenda se lo dice a la pantalla, para que no ofrezca «Calificar».
        List<Map> pasadas = get("/api/v1/me/bookings?scope=past", ana, List.class).getBody();
        assertThat(pasadas).filteredOn(b -> id.toString().equals(b.get("id")))
                .singleElement().satisfies(b -> assertThat(b.get("trial")).isEqualTo(true));
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El estudiante del ensayo tiene que ser un estudiante: la práctica solo la abre uno")
    void elEstudianteEsEstudiante() {
        createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        ResponseEntity<Map> r = post(RUTA, admin,
                Map.of("studentEmail", "juan@orion.test", "professorEmail", "maria@orion.test"), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) r.getBody().get("error")).contains("STUDENT");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Solo el administrador puede ensayar")
    void soloElAdmin() {
        ResponseEntity<Map> r = post(RUTA, sesionMaria,
                Map.of("studentEmail", "ana@orion.test", "professorEmail", "maria@orion.test"), Map.class);
        assertThat(r.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
        assertThat(get("/api/v1/admin/system/rehearsals", sesionMaria, Map.class).getStatusCode())
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
    }
}
