package co.orion.messaging.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.lifecycle.domain.LessonCompletedEvent;
import co.orion.messaging.application.RigelEnLosPrimerosPasos;
import co.orion.messaging.application.RigelTeExtrana;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.Booking;
import co.orion.scheduling.domain.BookingCreatedEvent;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * Los mensajes de Rigel (24/09/2026): oficiales, de solo lectura y pocos. La bienvenida al abrir el
 * hilo —al profesor, ya aprobado—, la primera reserva y la primera clase de cada lado una sola vez,
 * y «¿Seguimos?» a quien lleva semanas sin clase, a lo sumo una vez al mes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class MensajesDeRigelIT extends ApiIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private RigelEnLosPrimerosPasos primerosPasos;

    @Autowired
    private RigelTeExtrana teExtrana;

    private User ana;
    private User maria;

    @BeforeEach
    void seed() {
        jdbc.update("delete from rigel_messages");
        bookings.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        profiles.save(new ProfessorProfile(maria));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> hilo(String email) {
        return get("/api/v1/me/rigel", login(email), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<String> tipos(String email) {
        return ((List<Map<String, Object>>) hilo(email).get("messages")).stream()
                .map(m -> (String) m.get("kind")).toList();
    }

    private Booking dictada(Instant termino) {
        Booking b = TestBookings.confirmed(ana.getId(), maria.getId(), termino.minus(Duration.ofMinutes(55)),
                termino, BookingModality.VIRTUAL, null, ana.getId());
        b.closeWithAttendance(true, termino.plus(Duration.ofHours(1)));
        return bookings.saveAndFlush(b);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("El estudiante encuentra la bienvenida al abrir el hilo, una sola vez, con botones dentro de Orión")
    void bienvenidaDelEstudiante() {
        Map<String, Object> primero = hilo("ana@orion.test");
        assertThat(primero.get("unread")).isEqualTo(1);
        List<Map<String, Object>> mensajes = (List<Map<String, Object>>) primero.get("messages");
        assertThat(mensajes).hasSize(1);
        Map<String, Object> bienvenida = mensajes.get(0);
        assertThat(bienvenida.get("kind")).isEqualTo("WELCOME_STUDENT");
        assertThat((String) bienvenida.get("title")).contains("Ana").contains("Rigel");
        assertThat((List<Map<String, String>>) bienvenida.get("buttons"))
                .extracting(b -> b.get("href"))
                .containsExactly("/profesores", "/cuenta?seccion=ficha", "/diagnostico");

        // Abrirlo otra vez no la repite; marcarlo leído apaga el número.
        assertThat(tipos("ana@orion.test")).containsExactly("WELCOME_STUDENT");
        assertThat(post("/api/v1/me/rigel/read", login("ana@orion.test"), Map.of(), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(hilo("ana@orion.test").get("unread")).isEqualTo(0);
    }

    @Test
    @DisplayName("El profesor la recibe cuando ya está aprobado; el admin no tiene hilo")
    void bienvenidaDelProfesor() {
        assertThat(tipos("maria@orion.test")).isEmpty();
        approveTeacher(maria.getId());
        assertThat(tipos("maria@orion.test")).containsExactly("WELCOME_PROFESSOR");

        createUser("admin@orion.test", "Admin", UserRole.ADMIN);
        assertThat(get("/api/v1/me/rigel", login("admin@orion.test"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("La primera reserva y la primera clase, a cada lado y una sola vez")
    void primerosPasos() {
        approveTeacher(maria.getId());
        Booking primera = dictada(Instant.now().minus(2, ChronoUnit.DAYS));
        Booking segunda = dictada(Instant.now().minus(1, ChronoUnit.DAYS));

        primerosPasos.on(new BookingCreatedEvent(primera.getId()));
        primerosPasos.on(new BookingCreatedEvent(segunda.getId()));
        primerosPasos.on(new LessonCompletedEvent(primera.getId(), ana.getId(), Instant.now()));
        primerosPasos.on(new LessonCompletedEvent(segunda.getId(), ana.getId(), Instant.now()));

        assertThat(tipos("ana@orion.test"))
                .containsExactly("FIRST_BOOKING_STUDENT", "FIRST_CLASS_STUDENT", "WELCOME_STUDENT");
        assertThat(tipos("maria@orion.test"))
                .containsExactly("FIRST_BOOKING_PROFESSOR", "FIRST_CLASS_PROFESSOR", "WELCOME_PROFESSOR");

        List<Map<String, Object>> deAna = (List<Map<String, Object>>) hilo("ana@orion.test").get("messages");
        assertThat((String) deAna.get(0).get("body")).contains("Reservaste con María");
    }

    @Test
    @DisplayName("Una clase que todavía no termina no cuenta como la primera")
    void clasePorTerminar() {
        Booking enCurso = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().minus(10, ChronoUnit.MINUTES), BookingModality.VIRTUAL, null, ana.getId()));
        primerosPasos.on(new LessonCompletedEvent(enCurso.getId(), ana.getId(), Instant.now()));
        assertThat(tipos("ana@orion.test")).doesNotContain("FIRST_CLASS_STUDENT");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("«¿Seguimos?» a quien lleva dos semanas sin clase, con su profe a un botón, y no más de uno al mes")
    void teExtrana() {
        dictada(Instant.now().minus(20, ChronoUnit.DAYS));

        assertThat(teExtrana.recordar()).isEqualTo(1);
        assertThat(teExtrana.recordar()).isZero();

        List<Map<String, Object>> mensajes = (List<Map<String, Object>>) hilo("ana@orion.test").get("messages");
        Map<String, Object> seguimos = mensajes.stream().filter(m -> "COME_BACK".equals(m.get("kind"))).findFirst()
                .orElseThrow();
        assertThat((List<Map<String, String>>) seguimos.get("buttons"))
                .extracting(b -> b.get("href"))
                .contains("/profesores/" + maria.getId());

        // Pasado el mes, puede volver a escribirle.
        jdbc.update("update rigel_messages set created_at = now() - interval '31 days' where kind = 'COME_BACK'");
        assertThat(teExtrana.recordar()).isEqualTo(1);
    }

    @Test
    @DisplayName("Con una clase por venir, o una reciente, no le escribe")
    void conClasesNoExtrana() {
        dictada(Instant.now().minus(20, ChronoUnit.DAYS));
        UUID futura = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().plus(3, ChronoUnit.DAYS), BookingModality.VIRTUAL, null, ana.getId())).getId();
        assertThat(teExtrana.recordar()).isZero();

        bookings.deleteById(futura);
        dictada(Instant.now().minus(3, ChronoUnit.DAYS));
        assertThat(teExtrana.recordar()).isZero();
    }
}
