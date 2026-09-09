package co.orion.admin.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/**
 * Borrar definitivamente a un usuario, con la cuenta que un usuario de verdad tiene.
 *
 * <p>La purga borra a mano, tabla por tabla, todo lo que apunta a {@code users}. Esa lista es un
 * inventario que hay que ampliar cada vez que nace una tabla, y nadie lo estaba comprobando: no
 * había un solo test. Cada bloque le añadía filas que apuntan a la cuenta y la lista se quedaba
 * corta en silencio, hasta que el admin pulsaba Borrar y recibía un «Unexpected error» que no dice
 * qué tabla lo bloqueó.
 *
 * <p>Este test existe para que la siguiente tabla que se olvide falle aquí y no en producción.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class PurgaDeUsuarioIT extends ApiIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private Session admin;
    private UUID adminId;
    private User estudiante;

    @BeforeEach
    void seed() {
        users.deleteAll();
        adminId = createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN).getId();
        createUser("admin2@orion.test", "Segundo Admin", UserRole.ADMIN);
        estudiante = createUser("ana@orion.test", "Ana Ramírez", UserRole.STUDENT);
        admin = login("admin@orion.test");
    }

    /** Una cuenta sin nada colgando. Si esto falla, está roto de raíz. */
    @Test
    void unaCuentaLimpiaSeBorra() {
        assertThat(purgar(estudiante.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(estudiante.getId())).isEmpty();
    }

    /** La cuenta que ha usado Orión: una solicitud de soporte, con mensajes de las dos partes. */
    @Test
    void unaCuentaConSoporteTambien() {
        UUID ticket = abrirTicket(estudiante.getId());
        escribir(ticket, estudiante.getId(), "No me carga la foto");
        // La respuesta del admin vive en el hilo del estudiante, pero es de otra cuenta.
        escribir(ticket, adminId, "Ya lo estamos mirando");

        assertThat(purgar(estudiante.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(estudiante.getId())).isEmpty();
    }

    /**
     * Y el admin que ha administrado: contestó un ticket ajeno y tocó un ajuste. Esas filas apuntan
     * a cuentas y tablas que NO se van con la suya.
     */
    @Test
    void unAdminQueHaTrabajadoTambienSeBorra() {
        UUID segundo = users.findByEmailIgnoreCase("admin2@orion.test").orElseThrow().getId();
        UUID ticket = abrirTicket(estudiante.getId());
        escribir(ticket, segundo, "Contestado por el segundo admin");
        // Deja rastro en platform_settings.updated_by y en el historial de cambios.
        jdbc.update("update platform_settings set updated_by = ? where key = 'student_cancel_hours'", segundo);
        jdbc.update("""
                insert into platform_setting_changes (key, old_value, new_value, changed_by, changed_at)
                values ('student_cancel_hours', '12', '12', ?, now())
                """, segundo);

        assertThat(purgar(segundo).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findById(segundo)).isEmpty();
        // El ticket del estudiante sigue en pie: no era suyo.
        assertThat(jdbc.queryForObject("select count(*) from support_tickets where id = ?",
                Integer.class, ticket)).isEqualTo(1);
    }

    private UUID abrirTicket(UUID userId) {
        String code = "ORN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                insert into support_tickets (code, user_id, category, subject, status)
                values (?, ?, 'CUENTA', 'Una duda', 'OPEN')
                """, code, userId);
        return jdbc.queryForObject("select id from support_tickets where code = ?", UUID.class, code);
    }

    private void escribir(UUID ticketId, UUID autor, String texto) {
        jdbc.update("insert into support_messages (ticket_id, author_id, body) values (?, ?, ?)",
                ticketId, autor, texto);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> purgar(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE,
                "ORION_SESSION=" + admin.cookie() + "; XSRF-TOKEN=" + admin.csrfToken());
        headers.add("X-XSRF-TOKEN", admin.csrfToken());
        return rest.exchange("/api/v1/admin/users/" + userId + "/purge", HttpMethod.DELETE,
                new HttpEntity<>(Map.of("confirm", "BORRAR", "reason", "prueba"), headers), Map.class);
    }
}
