package co.orion.messaging.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.messaging.application.NotificationService;
import co.orion.messaging.persistence.NotificationRepository;
import co.orion.support.ApiIntegrationSupport;

/**
 * Borrar una notificación la borra de verdad.
 *
 * <p>Se descartó ocultarla con una marca: una notificación que el usuario borra y sigue en la base
 * es una promesa a medias. Lo que este test fija además es lo que NO se puede borrar — lo ajeno, y
 * lo que todavía no se ha leído cuando se vacía de golpe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class BorrarNotificacionesIT extends ApiIntegrationSupport {

    private static final String RUTA = "/api/v1/me/notifications";

    @Autowired
    private NotificationService notifications;

    @Autowired
    private NotificationRepository repo;

    private User ana;
    private User carlos;
    private Session anaSession;

    @BeforeEach
    void seed() {
        repo.deleteAll();
        users.deleteAll();
        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        carlos = createUser("carlos@orion.test", "Carlos Díaz", UserRole.STUDENT);
        anaSession = login("ana@orion.test");
    }

    private UUID crear(User para, String titulo) {
        return notifications.create(para.getId(), "TEST", titulo, null, "/mis-clases").getId();
    }

    @Test
    @DisplayName("Borrar una notificación propia la quita de la base")
    void borrarLaPropiaLaQuita() {
        UUID id = crear(ana, "Tu clase es mañana");

        assertThat(delete(RUTA + "/" + id, anaSession, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(repo.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("La notificación de otro no existe para ti")
    void laAjenaNoSeBorra() {
        UUID deCarlos = crear(carlos, "Tu pago se liberó");

        assertThat(delete(RUTA + "/" + deCarlos, anaSession, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(repo.findById(deCarlos)).isPresent();
    }

    @Test
    @DisplayName("Vaciar las leídas respeta las que no se han visto")
    void vaciarSoloBorraLoLeido() {
        UUID leida = crear(ana, "Ya la viste");
        UUID sinLeer = crear(ana, "Esta no");
        notifications.markRead(ana.getId(), leida);

        assertThat(delete(RUTA + "/read", anaSession, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(repo.findById(leida)).isEmpty();
        assertThat(repo.findById(sinLeer)).isPresent();
    }

    @Test
    @DisplayName("Vaciar no toca las notificaciones de otra persona")
    void vaciarNoTocaLasAjenas() {
        UUID deCarlos = crear(carlos, "Tu clase se confirmó");
        notifications.markRead(carlos.getId(), deCarlos);
        UUID mia = crear(ana, "La mía");
        notifications.markRead(ana.getId(), mia);

        delete(RUTA + "/read", anaSession, Void.class);

        assertThat(repo.findById(mia)).isEmpty();
        assertThat(repo.findById(deCarlos)).isPresent();
        assertThat(List.of()).isEmpty();
    }
}
