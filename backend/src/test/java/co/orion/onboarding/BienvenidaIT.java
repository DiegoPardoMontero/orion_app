package co.orion.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

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
import co.orion.support.ApiIntegrationSupport;

/**
 * La bienvenida: el video de Sofía para el profesor aprobado, una vez, y los recorridos de cada
 * rol. Lo que se fija: a quién le toca el video, que «ya lo vi» no se duplique, y que el enlace que
 * se pega en Ajustes no pueda ser cualquier cosa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class BienvenidaIT extends ApiIntegrationSupport {

    private static final String VIDEO = "https://youtu.be/dQw4w9WgXcQ";
    private static final String RUTA = "/api/v1/me/onboarding";

    @Autowired
    private JdbcTemplate jdbc;

    private User maria;
    private Session sesionMaria;
    private Session sesionAna;

    @BeforeEach
    void seed() {
        users.deleteAll();
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        approveTeacher(maria.getId());
        createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
        createUser("juan@orion.test", "Juan Torres", UserRole.PROFESSOR);
        sesionMaria = login("maria@orion.test");
        sesionAna = login("ana@orion.test");
        video(VIDEO);
    }

    /**
     * Cambiar el ajuste por la API deja al admin como autor del cambio y en el historial; sin
     * soltarlo, la prueba siguiente no puede borrar usuarios.
     */
    @AfterEach
    void limpiar() {
        jdbc.update("delete from platform_setting_changes where key = 'professor_welcome_video_url'");
        video("");
    }

    private void video(String url) {
        jdbc.update("update platform_settings set value = ?, updated_by = null where key = 'professor_welcome_video_url'",
                url);
    }

    @SuppressWarnings("rawtypes")
    private Map estado(Session sesion) {
        ResponseEntity<Map> r = get(RUTA, sesion, Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El profesor aprobado ve el video una vez: después de marcarlo, queda como visto")
    void elVideoUnaVez() {
        assertThat((Map) estado(sesionMaria).get("welcomeVideo"))
                .containsEntry("url", VIDEO).containsEntry("seen", false);

        assertThat(post(RUTA + "/WELCOME_VIDEO", sesionMaria, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post(RUTA + "/WELCOME_VIDEO", sesionMaria, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat((Map) estado(sesionMaria).get("welcomeVideo")).containsEntry("seen", true);
        assertThat(jdbc.queryForObject("select count(*) from onboarding_steps where user_id = ?",
                Integer.class, maria.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin video en Ajustes no aparece nada, y el día que se pega les aparece a todos")
    void sinVideoNoHayBienvenida() {
        video("");
        assertThat(estado(sesionMaria).get("welcomeVideo")).isNull();

        video(VIDEO);
        assertThat(estado(sesionMaria).get("welcomeVideo")).isNotNull();
    }

    @Test
    @DisplayName("Un profesor sin aprobar y un estudiante no reciben el video")
    void soloElProfesorAprobado() {
        assertThat(estado(login("juan@orion.test")).get("welcomeVideo")).isNull();
        assertThat(estado(sesionAna).get("welcomeVideo")).isNull();
    }

    @Test
    @DisplayName("A cada quien le toca su recorrido una vez; al profesor, solo ya aprobado")
    void elRecorridoPendiente() {
        assertThat(estado(sesionAna).get("pendingTour")).isEqualTo("TOUR_STUDENT");
        assertThat(estado(sesionMaria).get("pendingTour")).isEqualTo("TOUR_PROFESSOR");
        assertThat(estado(login("juan@orion.test")).get("pendingTour")).isNull();

        post(RUTA + "/TOUR_PROFESSOR", sesionMaria, null, Void.class);
        assertThat(estado(sesionMaria).get("pendingTour")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Cada rol marca su recorrido; el del otro es 422 y un paso inventado, 404")
    void cadaRolSuRecorrido() {
        assertThat(post(RUTA + "/TOUR_STUDENT", sesionAna, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post(RUTA + "/TOUR_PROFESSOR", sesionAna, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(post(RUTA + "/WELCOME_VIDEO", sesionAna, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(post(RUTA + "/NO_EXISTE", sesionAna, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat((List<String>) estado(sesionAna).get("completed")).containsExactly("TOUR_STUDENT");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("En Ajustes el enlace del video tiene que ser https; vacío es «no hay video»")
    void elEnlaceSeValida() {
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        Session admin = login("admin@orion.test");
        String ruta = "/api/v1/admin/settings/professor_welcome_video_url";

        assertThat(put(ruta, admin, Map.of("value", "javascript:alert(1)"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(put(ruta, admin, Map.of("value", "http://youtu.be/x"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        ResponseEntity<Map> bueno = put(ruta, admin, Map.of("value", " https://vimeo.com/123456 "), Map.class);
        assertThat(bueno.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bueno.getBody()).containsEntry("value", "https://vimeo.com/123456").containsEntry("type", "ENLACE");
        assertThat(put(ruta, admin, Map.of("value", ""), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
