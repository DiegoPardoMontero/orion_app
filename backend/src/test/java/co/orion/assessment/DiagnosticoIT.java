package co.orion.assessment;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.assessment.persistence.ConfidenceAssessmentRepository;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/**
 * Las puertas del diagnóstico, cada una con su motivo exacto.
 *
 * <p>El brief las pide una por una y por una razón: un 403 genérico obliga a la persona a adivinar
 * qué le falta, y lo que le falta —confirmar el correo, autorizar el uso de su voz— es justamente
 * algo que puede resolver en un minuto si se lo dicen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class DiagnosticoIT extends ApiIntegrationSupport {

    private static final String EMPEZAR = "/api/v1/assessments";
    private static final String CONSENTIR = "/api/v1/me/voice-consent";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ConfidenceAssessmentRepository assessments;

    private User ana;
    private Session anaSession;

    @BeforeEach
    void seed() {
        jdbc.update("delete from assessment_turns");
        jdbc.update("delete from assessment_recommendations");
        jdbc.update("delete from confidence_assessments");
        jdbc.update("delete from voice_consents");
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        anaSession = login("ana@orion.test");
        jdbc.update("update platform_settings set value = 'true' where key = 'assessment_enabled'");
    }

    /** La verificación es una fecha, no un booleano: importa cuándo, no solo si. */
    private void correoVerificado(boolean si) {
        jdbc.update("update users set email_verified_at = ? where id = ?",
                si ? java.sql.Timestamp.from(java.time.Instant.now()) : null, ana.getId());
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> empezar() {
        return post(EMPEZAR, anaSession, Map.of("languageCode", "EN"), Map.class);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin correo confirmado no se empieza, y se dice exactamente eso")
    void sinCorreoConfirmado() {
        correoVerificado(false);

        ResponseEntity<Map> r = empezar();

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) r.getBody().get("error")).contains("Confirma tu correo");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin consentimiento de voz tampoco, y también se dice cuál es")
    void sinConsentimientoDeVoz() {
        correoVerificado(true);

        ResponseEntity<Map> r = empezar();

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) r.getBody().get("error")).contains("autorización para procesar tu voz");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El consentimiento se registra con su versión")
    void elConsentimientoSeRegistra() {
        ResponseEntity<Map> r = post(CONSENTIR, anaSession, Map.of(), Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).containsEntry("version", "1.0");
        // Y con circunstancias: una autorización sin ellas es una afirmación nuestra, no un hecho.
        assertThat(jdbc.queryForObject(
                "select count(*) from voice_consents where user_id = ? and ip_address is not null",
                Integer.class, ana.getId())).isEqualTo(1);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El diagnóstico de otra persona no existe para ti")
    void elAjenoDaCuatrocientosCuatro() {
        User carlos = createUser("carlos@orion.test", "Carlos Díaz", UserRole.STUDENT);
        UUID suyo = assessments.saveAndFlush(
                new co.orion.assessment.domain.ConfidenceAssessment(
                        carlos.getId(), "EN", 1, java.time.Instant.now())).getId();

        assertThat(get("/api/v1/assessments/" + suyo, anaSession, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/v1/assessments/" + suyo + "/abandon", anaSession, Map.of(), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un profesor sin reserva con el estudiante no ve su diagnóstico")
    void elProfesorSinReservaNoVeNada() {
        User maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        Session mariaSession = login("maria@orion.test");

        ResponseEntity<Map> r = get(
                "/api/v1/professors/me/students/" + ana.getId() + "/assessment",
                mariaSession, Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El diagnóstico nunca devuelve la transcripción")
    void laTranscripcionNoSale() {
        UUID mio = assessments.saveAndFlush(
                new co.orion.assessment.domain.ConfidenceAssessment(
                        ana.getId(), "EN", 1, java.time.Instant.now())).getId();

        ResponseEntity<Map> r = get("/api/v1/assessments/" + mio, anaSession, Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Ni el campo, ni nada que se le parezca: lo que dijo mientras practicaba no se devuelve.
        assertThat(r.getBody()).doesNotContainKeys("turns", "transcript", "transcripcion");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un profesor no puede empezar un diagnóstico: no se autoevalúa aquí")
    void elProfesorNoEmpieza() {
        createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        Session juanSession = login("juan@orion.test");

        assertThat(post(EMPEZAR, juanSession, Map.of("languageCode", "EN"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
