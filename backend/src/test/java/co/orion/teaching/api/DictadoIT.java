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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.scheduling.TestBookings;
import co.orion.scheduling.domain.BookingModality;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El dictado del profesor: sus mismas puertas que el acta —solo el profesor de la clase, solo una
 * clase cerrada—, solo audio y de hasta tres minutos, y con el tope de gasto del acta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class DictadoIT extends ApiIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingRepository bookings;

    private User maria;
    private User ana;

    @BeforeEach
    void seed() {
        jdbc.update("delete from ai_usage_log where feature = 'lesson_note'");
        jdbc.update("delete from lesson_notes");
        jdbc.update("delete from attendance_records");
        bookings.deleteAll();
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        ana = createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
        createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("update platform_settings set value = '30000' where key = 'ai_daily_budget_cop'");
        jdbc.update("delete from attendance_records");
        bookings.deleteAll();
    }

    private UUID claseDictada() {
        UUID id = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId())).getId();
        jdbc.update("update bookings set status = 'COMPLETED', completed_at = now() where id = ?", id);
        return id;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> dictar(Session sesion, UUID clase, String tipo, int segundos) {
        HttpHeaders parte = new HttpHeaders();
        parte.setContentType(MediaType.parseMediaType(tipo));
        LinkedMultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("file", new HttpEntity<>(new ByteArrayResource(new byte[] {1, 2, 3, 4}) {
            @Override
            public String getFilename() {
                return "dictado.webm";
            }
        }, parte));
        cuerpo.add("seconds", String.valueOf(segundos));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.add(HttpHeaders.COOKIE, "ORION_SESSION=" + sesion.cookie() + "; XSRF-TOKEN=" + sesion.csrfToken());
        h.add("X-XSRF-TOKEN", sesion.csrfToken());
        return rest.exchange("/api/v1/bookings/" + clase + "/lesson-note/dictation", HttpMethod.POST,
                new HttpEntity<>(cuerpo, h), Map.class);
    }

    @Test
    @DisplayName("El profesor de la clase dicta y le vuelve el texto para revisarlo en la caja")
    void dictaYVuelveElTexto() {
        ResponseEntity<?> r = dictar(login("maria@orion.test"), claseDictada(), "audio/webm;codecs=opus", 40);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) r.getBody()).get("text").toString()).contains("dictado de prueba");
    }

    @Test
    @DisplayName("Solo el profesor de esa clase: otro profesor 403, el estudiante 403")
    void soloElDeLaClase() {
        UUID clase = claseDictada();

        assertThat(dictar(login("juan@orion.test"), clase, "audio/webm", 40).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(dictar(login("ana@orion.test"), clase, "audio/webm", 40).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Solo audio, de hasta tres minutos, y sobre una clase ya cerrada")
    void lasPuertas() {
        Session sesion = login("maria@orion.test");
        UUID clase = claseDictada();

        assertThat(dictar(sesion, clase, "text/plain", 40).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(dictar(sesion, clase, "audio/webm", 181).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        UUID futura = bookings.saveAndFlush(TestBookings.confirmed(ana.getId(), maria.getId(),
                Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS),
                BookingModality.VIRTUAL, null, ana.getId())).getId();
        assertThat(dictar(sesion, futura, "audio/webm", 40).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("Con el tope del día gastado, el dictado se apaga con un mensaje que invita a escribir")
    void conElTopeGastado() {
        jdbc.update("update platform_settings set value = '0' where key = 'ai_daily_budget_cop'");

        ResponseEntity<?> r = dictar(login("maria@orion.test"), claseDictada(), "audio/webm", 40);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(((Map<?, ?>) r.getBody()).get("error").toString()).contains("escribir tus notas");
    }
}
