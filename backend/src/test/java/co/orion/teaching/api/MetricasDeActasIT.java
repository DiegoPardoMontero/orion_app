package co.orion.teaching.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;
import co.orion.teaching.application.TeachingAiBudget;

/**
 * Las cifras del acta (brief del Bloque 10, paso C1): el panel del admin —la distribución de
 * {@code edit_ratio} es la que decide si el borrador sirve— y el porcentaje de clases con acta que
 * el profesor ve en su desempeño.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class MetricasDeActasIT extends ApiIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private TeachingAiBudget presupuesto;

    private User maria;
    private User ana;
    private int hora;

    @BeforeEach
    void seed() {
        jdbc.update("delete from ai_usage_log where feature = 'lesson_note'");
        jdbc.update("delete from lesson_vocabulary");
        jdbc.update("delete from lesson_notes");
        jdbc.update("delete from attendance_records");
        bookings.deleteAll();
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana = createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        hora = 0;
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from lesson_notes");
        jdbc.update("delete from attendance_records");
        bookings.deleteAll();
    }

    /** Una clase de María con Ana, cerrada ahora; con acta publicada si {@code ratio} no es nulo. */
    private UUID clase(Double ratio) {
        Instant inicio = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS)
                .plus(Duration.ofHours(hora++));
        UUID id = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(), inicio,
                BookingModality.VIRTUAL, null, ana.getId())).getId();
        jdbc.update("update bookings set status = 'COMPLETED', completed_at = now() where id = ?", id);
        if (ratio != null) {
            jdbc.update("""
                    insert into lesson_notes (booking_id, professor_id, student_id, raw_input, status, origin,
                                              edit_ratio, published_at)
                    values (?, ?, ?, 'notas de la clase de hoy', 'PUBLISHED', 'AI_DRAFT', ?, now())
                    """, id, maria.getId(), ana.getId(), ratio);
        }
        return id;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("El panel cuenta clases con acta, reparte el edit_ratio por sus cortes y los resultados del proveedor")
    void elPanel() {
        clase(0.0);
        clase(0.03);
        clase(0.2);
        clase(0.8);
        clase(null);
        presupuesto.registrar(maria.getId(), "gpt-5-mini", 300, 100, 2_000, "OK");
        presupuesto.registrar(maria.getId(), "gpt-5-mini", null, null, 25_000, "TIMEOUT");

        Map panel = get("/api/v1/admin/lesson-notes/metrics", login("admin@orion.test"), Map.class).getBody();

        assertThat(panel).containsEntry("clasesCerradas", 5).containsEntry("clasesConActa", 4)
                .containsEntry("publicadasHoy", 4)
                .containsEntry("sinEditar", 2).containsEntry("edicionMenor", 1).containsEntry("reescritas", 1)
                .containsEntry("iaEncendida", true);
        assertThat((Map) panel.get("resultadosHoy")).containsEntry("OK", 1).containsEntry("TIMEOUT", 1);
        assertThat(((Number) panel.get("gastadoHoyCop")).longValue()).isPositive();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El profesor ve cuántas de sus clases tienen acta; el estudiante y el admin, esa ruta no")
    void elProfesor() {
        clase(0.1);
        clase(null);

        Map suyo = get("/api/v1/professors/me/lesson-notes/share", login("maria@orion.test"), Map.class).getBody();
        assertThat(suyo).containsEntry("clasesCerradas", 2).containsEntry("clasesConActa", 1).containsEntry("dias", 90);

        assertThat(get("/api/v1/professors/me/lesson-notes/share", login("ana@orion.test"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/admin/lesson-notes/metrics", login("maria@orion.test"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
