package co.orion.admin.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    /**
     * Quien probó el diagnóstico tiene gasto de IA a su nombre, y el profesor que se le recomendó
     * aparece en su resultado. Las dos filas bloqueaban la purga hasta la V47.
     */
    @Test
    void quienHizoElDiagnosticoYElProfesorRecomendadoTambienSeBorran() {
        User profesor = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR);
        UUID evaluacion = jdbc.queryForObject("""
                insert into confidence_assessments (user_id, language_code, sequence, status, started_at)
                values (?, 'EN', 1, 'IN_PROGRESS', now()) returning id
                """, UUID.class, estudiante.getId());
        jdbc.update("""
                insert into assessment_recommendations (assessment_id, professor_id, position, reason_code, reason_text)
                values (?, ?, 1, 'NIVEL', 'Por tu nivel')
                """, evaluacion, profesor.getId());
        jdbc.update("""
                insert into ai_usage_log (feature, actor_id, provider, voice_seconds, cost_cop, outcome)
                values ('confidence_assessment', ?, 'scripted', 120, 777, 'OK')
                """, estudiante.getId());

        assertThat(purgar(profesor.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(purgar(estudiante.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        // El gasto sigue contando para el tope del día; solo perdió a quién se cargó.
        assertThat(jdbc.queryForObject("select count(*) from ai_usage_log where actor_id is null and cost_cop = 777",
                Integer.class)).isEqualTo(1);
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

    /**
     * El acta y su práctica se van por la FK en cascada con la cuenta (brief del Bloque 10: «pedir su
     * borrado como cualquier otro dato suyo»), y la vista previa que el admin confirma lo dice.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void lasActasYSuPracticaSeVenEnLaVistaPreviaYSeBorran() {
        UUID maria = createUser("maria@orion.test", "María Gómez", UserRole.PROFESSOR).getId();
        UUID clase = jdbc.queryForObject("""
                insert into bookings (student_id, professor_id, starts_at, ends_at, status, modality, completed_at,
                                      created_by)
                values (?, ?, now() - interval '3 hours', now() - interval '2 hours', 'COMPLETED', 'VIRTUAL', now(), ?)
                returning id""", UUID.class, estudiante.getId(), maria, estudiante.getId());
        UUID acta = jdbc.queryForObject("""
                insert into lesson_notes (booking_id, professor_id, student_id, raw_input)
                values (?, ?, ?, 'Trabajamos past simple.') returning id""", UUID.class, clase, maria, estudiante.getId());
        jdbc.update("""
                insert into practice_sets (student_id, lesson_note_id, professor_id, material, expires_at)
                values (?, ?, ?, '{}'::jsonb, now() + interval '7 days')""", estudiante.getId(), acta, maria);

        Map vista = get("/api/v1/admin/users/" + estudiante.getId() + "/purge-preview", admin, Map.class).getBody();
        assertThat((List<Map>) vista.get("rows")).anySatisfy(r ->
                assertThat(r).containsEntry("what", "Actas de clase escritas o recibidas").containsEntry("count", 1));
        assertThat((List<Map>) vista.get("rows")).anySatisfy(r ->
                assertThat(r).containsEntry("what", "Sets de práctica").containsEntry("count", 1));

        assertThat(purgar(estudiante.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select count(*) from lesson_notes", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from practice_sets", Integer.class)).isZero();
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
