package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.ProfessorProfile;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.identity.persistence.ProfessorProfileRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * El enlace para invitar estudiantes (V63): con el nombre del profe, único, fijo una vez creado, y
 * público solo si su perfil está publicado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class InvitarEstudiantesIT extends ApiIntegrationSupport {

    @Autowired
    private ProfessorProfileRepository profiles;

    private User maria;
    private User otraMaria;

    @BeforeEach
    void seed() {
        profiles.deleteAll();
        users.deleteAll();
        maria = profesora("maria@orion.test", true);
        otraMaria = profesora("maria2@orion.test", false);
        createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
    }

    private User profesora(String correo, boolean publicada) {
        User u = createUser(correo, "María Gómez", UserRole.PROFESSOR);
        ProfessorProfile p = new ProfessorProfile(u);
        p.changeRate(45_000L);
        if (publicada) {
            p.publish();
        }
        profiles.save(p);
        approveTeacher(u.getId());
        return u;
    }

    @SuppressWarnings("rawtypes")
    private String enlaceDe(String correo) {
        return (String) get("/api/v1/me/profile/invite-link", login(correo), Map.class).getBody().get("slug");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Con su nombre, único entre tocayos, y el mismo cada vez")
    void unicoYFijo() {
        assertThat(enlaceDe("maria@orion.test")).isEqualTo("maria-gomez");
        assertThat(enlaceDe("maria2@orion.test")).isEqualTo("maria-gomez-2");
        assertThat(enlaceDe("maria@orion.test")).isEqualTo("maria-gomez");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Lleva al perfil sin cuenta, y solo si el perfil está publicado")
    void publicoSoloSiPublicado() {
        enlaceDe("maria@orion.test");
        enlaceDe("maria2@orion.test");

        var anonimo = rest.getForEntity("/api/v1/professors/by-slug/maria-gomez", Map.class);
        assertThat(anonimo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(anonimo.getBody()).containsEntry("id", maria.getId().toString());

        assertThat(rest.getForEntity("/api/v1/professors/by-slug/maria-gomez-2", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/v1/professors/by-slug/nadie", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Solo un profesor pide su enlace")
    void soloProfesores() {
        assertThat(get("/api/v1/me/profile/invite-link", login("ana@orion.test"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
