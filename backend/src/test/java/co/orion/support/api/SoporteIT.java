package co.orion.support.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.persistence.SupportMessageRepository;
import co.orion.support.persistence.SupportTicketRepository;
import co.orion.support.ApiIntegrationSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class SoporteIT extends ApiIntegrationSupport {

    @Autowired
    private SupportTicketRepository tickets;

    @Autowired
    private SupportMessageRepository mensajes;

    private User ana;
    private User maria;
    private Session sesionAna;
    private Session sesionMaria;
    private Session sesionAdmin;

    @BeforeEach
    void seed() {
        mensajes.deleteAll();
        tickets.deleteAll();
        users.deleteAll();

        ana = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);

        sesionAna = login("ana@orion.test");
        sesionMaria = login("maria@orion.test");
        sesionAdmin = login("admin@orion.test");
    }

    private ResponseEntity<Map> abrir(Session sesion, String categoria, String asunto) {
        return post("/api/v1/me/support/tickets", sesion, Map.of(
                "category", categoria, "subject", asunto, "body", "Cuento lo que pasó."), Map.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Abrir una solicitud crea el hilo con su código y su primer mensaje")
    void abrirCreaElHilo() {
        ResponseEntity<Map> respuesta = abrir(sesionAna, "CLASE", "No pude entrar a la sala");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> ticket = (Map<String, Object>) respuesta.getBody().get("ticket");
        assertThat((String) ticket.get("code")).startsWith("ORN-");
        assertThat(ticket).containsEntry("status", "OPEN");
        assertThat((List<?>) respuesta.getBody().get("messages")).hasSize(1);
    }

    /**
     * El plazo no es una promesa comercial: lo fija el art. 15 de la Ley 1581 de 2012. Y donde la
     * ley no lo fija, no se inventa uno.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Las categorías de habeas data llevan vencimiento; las demás, no")
    void soloLasCategoriasLegalesLlevanPlazo() {
        Map<String, Object> reclamo = (Map<String, Object>)
                abrir(sesionAna, "HABEAS_DATA_RECLAMO", "Quiero que borren mis datos")
                        .getBody().get("ticket");
        Map<String, Object> otro = (Map<String, Object>)
                abrir(sesionAna, "OTRO", "Una duda cualquiera").getBody().get("ticket");

        assertThat(reclamo.get("dueAt")).isNotNull();
        assertThat(otro.get("dueAt")).isNull();
    }

    /** Un profesor abre tickets igual que un estudiante: es el otro lado del marketplace. */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un profesor también puede abrir una solicitud")
    void elProfesorTambienAbre() {
        assertThat(abrir(sesionMaria, "PAGO", "No me llegó la liquidación").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Cada quien ve solo sus solicitudes")
    void cadaQuienVeLoSuyo() {
        abrir(sesionAna, "CUENTA", "Lo de Ana");
        abrir(sesionMaria, "CUENTA", "Lo de María");

        List<Map<String, Object>> deAna =
                get("/api/v1/me/support/tickets", sesionAna, List.class).getBody();

        assertThat(deAna).hasSize(1);
        assertThat(deAna.getFirst()).containsEntry("subject", "Lo de Ana");
    }

    /** 404 y no 403: confirmar que el ticket existe ya sería decir algo de otra persona. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("El ticket de otra persona responde 404, no 403")
    void elTicketAjenoEs404() {
        String code = (String) ((Map<String, Object>)
                abrir(sesionAna, "CUENTA", "Lo de Ana").getBody().get("ticket")).get("code");

        ResponseEntity<Map> respuesta =
                get("/api/v1/me/support/tickets/" + code, sesionMaria, Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Cuando contesta el admin queda respondida; cuando contesta el dueño, vuelve a abierta")
    void elEstadoLoDecideQuienEscribe() {
        String code = (String) ((Map<String, Object>)
                abrir(sesionAna, "CLASE", "Algo pasó").getBody().get("ticket")).get("code");

        ResponseEntity<Map> deAdmin = post("/api/v1/admin/support/tickets/" + code + "/replies",
                sesionAdmin, Map.of("body", "Lo revisamos y ya está."), Map.class);
        assertThat(((Map<String, Object>) deAdmin.getBody().get("ticket")))
                .containsEntry("status", "ANSWERED");

        ResponseEntity<Map> deAna = post("/api/v1/me/support/tickets/" + code + "/replies",
                sesionAna, Map.of("body", "Sigue igual."), Map.class);
        assertThat(((Map<String, Object>) deAna.getBody().get("ticket")))
                .containsEntry("status", "OPEN");
    }

    /**
     * Dar por zanjado algo que la otra persona no da por zanjado convierte un soporte en un muro.
     * Escribir reabre incluso una cerrada.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("Escribir en una solicitud cerrada la reabre")
    void escribirReabreUnaCerrada() {
        String code = (String) ((Map<String, Object>)
                abrir(sesionAna, "CLASE", "Algo pasó").getBody().get("ticket")).get("code");
        post("/api/v1/admin/support/tickets/" + code + "/close", sesionAdmin, null, Map.class);

        ResponseEntity<Map> respuesta = post("/api/v1/me/support/tickets/" + code + "/replies",
                sesionAna, Map.of("body", "No quedó resuelto."), Map.class);

        assertThat(((Map<String, Object>) respuesta.getBody().get("ticket")))
                .containsEntry("status", "OPEN");
    }

    /**
     * La bandeja ordena por lo que vence antes. Por fecha de creación, un «no me carga la foto» de
     * hace tres días iría por delante de un reclamo de habeas data que vence mañana.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("La bandeja del admin pone primero lo que vence antes")
    void laBandejaOrdenaPorVencimiento() {
        abrir(sesionAna, "OTRO", "Sin plazo");
        abrir(sesionAna, "HABEAS_DATA_RECLAMO", "Con plazo de 15 hábiles");
        abrir(sesionAna, "HABEAS_DATA_CONSULTA", "Con plazo de 10 hábiles");

        List<Map<String, Object>> bandeja =
                get("/api/v1/admin/support/tickets", sesionAdmin, List.class).getBody();

        assertThat(bandeja).extracting(t -> t.get("subject"))
                .containsExactly("Con plazo de 10 hábiles", "Con plazo de 15 hábiles", "Sin plazo");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Una categoría inventada responde 422 con las válidas, no un 500")
    void categoriaInventadaEs422() {
        ResponseEntity<Map> respuesta = abrir(sesionAna, "NO_EXISTO", "Lo que sea");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(String.valueOf(respuesta.getBody().get("error"))).contains("HABEAS_DATA_CONSULTA");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un estudiante no entra a la bandeja del administrador")
    void laBandejaEsSoloDelAdmin() {
        assertThat(get("/api/v1/admin/support/tickets", sesionAna, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
