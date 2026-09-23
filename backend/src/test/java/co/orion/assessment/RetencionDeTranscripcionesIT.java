package co.orion.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.assessment.application.AssessmentService;
import co.orion.assessment.application.TranscriptRetentionJob;
import co.orion.assessment.domain.ConfidenceAssessment;
import co.orion.assessment.persistence.AssessmentTurnRepository;
import co.orion.assessment.persistence.ConfidenceAssessmentRepository;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.UserRepository;

/**
 * La retención: se borra lo que se dijo, se conserva cómo se dijo.
 *
 * <p>Esa distinción es el punto entero. La curva de progreso de alguien entre un diagnóstico y el
 * siguiente sobrevive porque vive en las señales —números sobre cómo habló—; el texto de lo que
 * dijo mientras practicaba, no. Guardar indefinidamente la transcripción de una persona insegura
 * hablando un idioma que no domina no tiene justificación, ni de producto ni pasada la fecha.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RetencionDeTranscripcionesIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TranscriptRetentionJob job;

    @Autowired
    private AssessmentService service;

    @Autowired
    private ConfidenceAssessmentRepository assessments;

    @Autowired
    private AssessmentTurnRepository turns;

    @Autowired
    private UserRepository users;

    private User ana;

    @BeforeEach
    void seed() {
        jdbc.update("delete from assessment_turns");
        jdbc.update("delete from confidence_assessments");
        jdbc.update("delete from voice_consents");
        users.deleteAll();
        ana = users.saveAndFlush(
                new User("ana@orion.test", "$2a$10$abcdefghijklmnopqrstuv", "Ana Ramírez",
                        UserRole.STUDENT));
    }

    private UUID conTranscripcion(User de, Instant cuando) {
        ConfidenceAssessment a = assessments.saveAndFlush(
                new ConfidenceAssessment(de.getId(), "EN", 1, cuando));
        jdbc.update("update confidence_assessments set started_at = ?, status = 'COMPLETED', "
                + "score = 70, summary = 'Sostuviste la conversación.', completed_at = ? where id = ?",
                java.sql.Timestamp.from(cuando), java.sql.Timestamp.from(cuando), a.getId());
        jdbc.update("insert into assessment_turns "
                + "(assessment_id, turn_index, speaker, transcript, word_count, latency_ms) "
                + "values (?, 0, 'USER', 'I work in logistics every day', 6, 500)", a.getId());
        return a.getId();
    }

    @Test
    @DisplayName("Pasado el plazo se borra el texto y se conservan puntaje, resumen y señales")
    void elPlazoBorraElTextoYNadaMas() {
        UUID vieja = conTranscripcion(ana, Instant.now().minusSeconds(400L * 24 * 3600));

        // Por run(), que es lo que llama el programador de tareas de madrugada.
        job.run();

        assertThat(turns.countWithTranscript(List.of(vieja))).isZero();
        // Y lo que sostiene la curva sigue ahí: el número y la señal, que no son lo que dijo.
        assertThat(jdbc.queryForObject(
                "select score from confidence_assessments where id = ?", Integer.class, vieja))
                .isEqualTo(70);
        assertThat(jdbc.queryForObject(
                "select word_count from assessment_turns where assessment_id = ?",
                Integer.class, vieja)).isEqualTo(6);
    }

    @Test
    @DisplayName("Dentro del plazo no se toca nada")
    void loRecienteSeQueda() {
        UUID reciente = conTranscripcion(ana, Instant.now().minusSeconds(3600));

        job.purgar();

        assertThat(turns.countWithTranscript(List.of(reciente))).isEqualTo(1);
    }

    @Test
    @DisplayName("Revocar el consentimiento borra el texto sin esperar al plazo")
    void revocarNoEspera() {
        UUID reciente = conTranscripcion(ana, Instant.now().minusSeconds(3600));
        service.acceptConsent(ana, "127.0.0.1", "test");
        service.revokeConsent(ana);

        job.purgar();

        // Retirar la autorización tiene que significar algo el mismo día, no dentro de un año.
        assertThat(turns.countWithTranscript(List.of(reciente))).isZero();
    }
}
