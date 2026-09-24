package co.orion.scheduling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.scheduling.application.RecordatorioDelPerfil;
import co.orion.scheduling.domain.AvailabilityRule;
import co.orion.scheduling.persistence.AvailabilityRuleRepository;
import co.orion.support.ApiIntegrationSupport;
import jakarta.mail.internet.MimeMessage;

/**
 * El perfil del profesor que todavía no recibe estudiantes (24/09/2026): qué le falta, y los mismos
 * recordatorios que la ficha del estudiante —día 1 en la app, día 2 por correo, día 3 en la app—,
 * contados desde la aprobación y hasta que esté completo y publicado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class PerfilDelProfesorIT extends ApiIntegrationSupport {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProfessorProfileRepository profiles;

    @Autowired
    private AvailabilityRuleRepository rules;

    @Autowired
    private RecordatorioDelPerfil recordatorio;

    private User maria;

    @BeforeEach
    void seed() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from profile_reminders");
        rules.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        profiles.save(new ProfessorProfile(maria));
        approveTeacher(maria.getId());
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    }

    @SuppressWarnings("unchecked")
    private List<String> pendiente() {
        return (List<String>) get("/api/v1/me/profile/pending", login("maria@orion.test"), Map.class)
                .getBody().get("missing");
    }

    private void aprobadaHace(int horas) {
        jdbc.update("update teacher_applications set reviewed_at = now() - make_interval(hours => ?) where user_id = ?",
                horas, maria.getId());
    }

    private int avisos() {
        return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'PROFILE_NUDGE'",
                Integer.class, maria.getId());
    }

    @Test
    @DisplayName("Dice qué falta, en orden, y nada cuando está completo, con horarios y publicado")
    void queFalta() {
        assertThat(pendiente()).containsExactly("FOTO", "TITULAR", "DESCRIPCION", "TARIFA", "IDIOMAS", "HORARIOS", "PUBLICAR");

        jdbc.update("update users set photo_url = 'https://img.orion.test/maria.png' where id = ?", maria.getId());
        jdbc.update("""
                update professor_profiles set headline = 'Inglés conversacional para tu trabajo',
                       bio = 'Clases prácticas', hourly_rate_cop = 45000 where user_id = ?
                """, maria.getId());
        jdbc.update("insert into professor_languages (professor_id, language_code, is_native) values (?, 'EN', false)",
                maria.getId());
        assertThat(pendiente()).containsExactly("HORARIOS", "PUBLICAR");

        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(21, 0)));
        jdbc.update("update professor_profiles set is_published = true where user_id = ?", maria.getId());
        assertThat(pendiente()).isEmpty();
    }

    @Test
    @DisplayName("Día 1 en la app, día 2 por correo, día 3 en la app; nunca dos el mismo día, y nada más")
    void losTresRecordatorios() {
        aprobadaHace(10);
        assertThat(recordatorio.recordar()).isZero();

        aprobadaHace(24 * 5);
        assertThat(recordatorio.recordar()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos() == 1);
        assertThat(recordatorio.recordar()).isZero();

        jdbc.update("update profile_reminders set sent_at = now() - interval '21 hours' where user_id = ?", maria.getId());
        assertThat(recordatorio.recordar()).isEqualTo(1);
        verify(mailSender, timeout(5000)).send(any(MimeMessage.class));
        assertThat(avisos()).isEqualTo(1);

        jdbc.update("update profile_reminders set sent_at = now() - interval '21 hours' where user_id = ?", maria.getId());
        assertThat(recordatorio.recordar()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos() == 2);

        jdbc.update("update profile_reminders set sent_at = now() - interval '5 days' where user_id = ?", maria.getId());
        assertThat(recordatorio.recordar()).isZero();
    }

    @Test
    @DisplayName("Publicado y con horarios ya recibe reservas: el aviso pide terminar el perfil, no dice que no recibe")
    void publicadoSinFoto() {
        jdbc.update("""
                update professor_profiles set headline = 'Inglés conversacional para tu trabajo',
                       bio = 'Clases prácticas', hourly_rate_cop = 45000, is_published = true where user_id = ?
                """, maria.getId());
        jdbc.update("insert into professor_languages (professor_id, language_code, is_native) values (?, 'EN', false)",
                maria.getId());
        rules.save(new AvailabilityRule(maria.getId(), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(21, 0)));
        assertThat(pendiente()).containsExactly("FOTO");

        aprobadaHace(24 * 5);
        assertThat(recordatorio.recordar()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos() == 1);
        assertThat(jdbc.queryForObject("select title from notifications where user_id = ? and type = 'PROFILE_NUDGE'",
                String.class, maria.getId())).isEqualTo("Rigel: termina tu perfil");
    }

    @Test
    @DisplayName("Un estudiante no pide lo que le falta a un perfil de profesor")
    void soloProfesores() {
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        assertThat(get("/api/v1/me/profile/pending", login("ana@orion.test"), Map.class).getStatusCode().value())
                .isEqualTo(403);
    }
}
