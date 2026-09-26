package co.orion.legal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.billing.application.PayoutHolds;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/**
 * El acuerdo del profesor con versiones (brief de liquidaciones, paso 1). La 2.0 trae el mandato de
 * recaudo: quien enseña tiene que aceptarla, y mientras no lo haga sus liquidaciones quedan retenidas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class AcuerdoDelProfesorIT extends ApiIntegrationSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PayoutHolds holds;

    private final List<UUID> creados = new java.util.ArrayList<>();

    @AfterEach
    void limpiar() {
        for (UUID id : creados) {
            jdbc.update("delete from agreement_acceptances where user_id = ?", id);
            jdbc.update("delete from teacher_applications where user_id = ?", id);
            jdbc.update("delete from student_profiles where user_id = ?", id);
            jdbc.update("delete from users where id = ?", id);
        }
    }

    @SuppressWarnings("rawtypes")
    @Test
    void elAcuerdoVigenteEsPublicoYTraeElMandato() {
        ResponseEntity<Map> doc = rest.getForEntity("/api/v1/legal/TEACHER_AGREEMENT", Map.class);

        assertThat(doc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(doc.getBody()).containsEntry("version", "2.0").containsEntry("title", "Acuerdo del profesor");
        assertThat((String) doc.getBody().get("body")).contains("**Mandato de recaudo.**")
                .contains("tercer día hábil después del corte");
        // La 1.0 sigue ahí: es la prueba de lo que aceptaron los profes de antes.
        assertThat(rest.getForEntity("/api/v1/legal/TEACHER_AGREEMENT/1.0", Map.class).getBody())
                .containsEntry("version", "1.0");
    }

    /**
     * Un profe que solo aceptó la 1.0 tiene pendiente la 2.0: la app se la pide al entrar y su
     * liquidación queda retenida. Al aceptarla queda la constancia con su versión y su fecha, y la
     * retención se levanta.
     */
    @SuppressWarnings("rawtypes")
    @Test
    void sinLaVersionConElMandatoSusLiquidacionesQuedanRetenidas() {
        User maria = profe();
        jdbc.update("insert into agreement_acceptances (user_id, document_code, version, accepted_at) values (?, 'TEACHER_AGREEMENT', '1.0', now())",
                maria.getId());
        Session sesion = login(maria.getEmail());

        assertThat(get("/api/v1/me/legal/pending", sesion, Map.class).getBody())
                .containsEntry("documents", List.of("TEACHER_AGREEMENT"));
        assertThat(holds.motivo(maria.getId())).contains(PayoutHolds.SIN_MANDATO);

        ResponseEntity<Void> aceptar = post("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", sesion, null, Void.class);
        assertThat(aceptar.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // Dos veces es lo mismo que una: la constancia no se duplica.
        post("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", sesion, null, Void.class);

        Map<String, Object> constancia = jdbc.queryForMap(
                "select version, accepted_at from agreement_acceptances where user_id = ? and version = '2.0'", maria.getId());
        assertThat(constancia.get("accepted_at")).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from agreement_acceptances where user_id = ? and version = '2.0'",
                Integer.class, maria.getId())).isEqualTo(1);
        assertThat(get("/api/v1/me/legal/pending", sesion, Map.class).getBody())
                .containsEntry("documents", List.of());
        assertThat(holds.motivo(maria.getId())).isEmpty();
    }

    /** A quien no enseña no se le pide el acuerdo del profesor. */
    @SuppressWarnings("rawtypes")
    @Test
    void aUnEstudianteNoSeLePide() {
        User ana = createUser("ana." + UUID.randomUUID() + "@orion.test", "Ana Ramírez", UserRole.STUDENT);
        creados.add(ana.getId());

        assertThat(get("/api/v1/me/legal/pending", login(ana.getEmail()), Map.class).getBody())
                .containsEntry("documents", List.of());
    }

    private User profe() {
        User maria = createUser("maria." + UUID.randomUUID() + "@orion.test", "María Gómez", UserRole.PROFESSOR);
        creados.add(maria.getId());
        approveTeacher(maria.getId());
        return maria;
    }
}
