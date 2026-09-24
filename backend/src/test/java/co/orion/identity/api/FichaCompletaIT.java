package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.application.RecordatorioDeFicha;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/**
 * La ficha con énfasis (24/09/2026): recordatorios que no persiguen para siempre —día 1 en la app,
 * día 2 por correo, día 3 en la app, cada uno una vez— y el logro «Ficha completa».
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class FichaCompletaIT extends ApiIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordatorioDeFicha recordatorio;

    private User ana;

    @BeforeEach
    void seed() {
        jdbc.update("delete from notifications");
        jdbc.update("delete from profile_reminders");
        jdbc.update("delete from student_goals");
        users.deleteAll();
        ana = createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
    }

    private void cuentaDeHace(int horas) {
        jdbc.update("update users set created_at = now() - make_interval(hours => ?) where id = ?", horas, ana.getId());
    }

    private int avisos() {
        return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'PROFILE_NUDGE'",
                Integer.class, ana.getId());
    }

    private int pasos() {
        return jdbc.queryForObject("select count(*) from profile_reminders where user_id = ?", Integer.class, ana.getId());
    }

    @Test
    @DisplayName("Una cuenta de menos de un día no recibe nada; al día, el primer aviso, una sola vez")
    void elPrimerAviso() {
        cuentaDeHace(10);
        assertThat(recordatorio.recordar()).isZero();

        cuentaDeHace(25);
        assertThat(recordatorio.recordar()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos() == 1);
        assertThat(recordatorio.recordar()).isZero();
        assertThat(pasos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una cuenta vieja no recibe los tres el mismo día: entre paso y paso pasan 20 horas")
    void noTodosElMismoDia() {
        cuentaDeHace(24 * 30);
        assertThat(recordatorio.recordar()).isEqualTo(1);
        assertThat(recordatorio.recordar()).isZero();

        jdbc.update("update profile_reminders set sent_at = now() - interval '21 hours' where user_id = ?", ana.getId());
        assertThat(recordatorio.recordar()).isEqualTo(1);
        // El del día 2 es el correo: no suma avisos en la campana.
        await().atMost(Duration.ofSeconds(5)).until(() -> pasos() == 2);
        assertThat(avisos()).isEqualTo(1);

        jdbc.update("update profile_reminders set sent_at = now() - interval '21 hours' where user_id = ?", ana.getId());
        assertThat(recordatorio.recordar()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> avisos() == 2);

        // Y ahí se acaba: tres y nada más.
        jdbc.update("update profile_reminders set sent_at = now() - interval '5 days' where user_id = ?", ana.getId());
        assertThat(recordatorio.recordar()).isZero();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Con la ficha completa no hay recordatorio, y se enciende «Ficha completa»")
    void completaEnciendeElLogro() {
        cuentaDeHace(25);
        jdbc.update("update users set photo_url = 'https://img.orion.test/ana.png' where id = ?", ana.getId());
        Session sesion = login("ana@orion.test");
        put("/api/v1/me/student-profile", sesion, Map.of(
                "selfDeclaredLevel", "INTERMEDIATE", "primaryLanguage", "EN",
                "motivation", "Quiero presentar entrevistas en inglés.", "goalCodes", List.of("INTERVIEW")), Map.class);

        assertThat(recordatorio.recordar()).isZero();
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            List<Map> logros = get("/api/v1/me/achievements", sesion, List.class).getBody();
            return logros.stream().anyMatch(l -> "compromiso-ficha-completa".equals(l.get("code"))
                    && Boolean.TRUE.equals(l.get("unlocked")));
        });
    }
}
