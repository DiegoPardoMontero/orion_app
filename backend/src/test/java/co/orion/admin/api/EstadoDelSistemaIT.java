package co.orion.admin.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.UserRole;
import co.orion.support.ApiIntegrationSupport;

/**
 * Administración → Sistema: lo que dice de cada integración, sin secretos, y solo al admin. En los
 * tests no hay llaves, así que todo lo que depende de una sale apagado y con su motivo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class EstadoDelSistemaIT extends ApiIntegrationSupport {

    @BeforeEach
    void seed() {
        users.deleteAll();
        createUser("admin@orion.test", "Orion Admin", UserRole.ADMIN);
        createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    @DisplayName("El admin ve la IA del diagnóstico y la del acta, apagadas y con su motivo y sus variables")
    void laIaSeVeApagadaConSuMotivo() {
        Map estado = get("/api/v1/admin/system/status", login("admin@orion.test"), Map.class).getBody();
        List<Map> integraciones = (List<Map>) estado.get("integraciones");

        assertThat(integraciones).extracting(i -> i.get("nombre"))
                .contains("Diagnóstico de voz (OpenAI)", "Borrador del acta (OpenAI)");
        assertThat(integraciones).filteredOn(i -> "Borrador del acta (OpenAI)".equals(i.get("nombre")))
                .singleElement().satisfies(i -> {
                    assertThat(i.get("configurada")).isEqualTo(false);
                    assertThat((String) i.get("siFalta")).contains("regla simple");
                    assertThat((List<String>) i.get("variables")).contains("OPENAI_API_KEY");
                });
    }

    @Test
    @DisplayName("Un estudiante no lo ve")
    void soloElAdmin() {
        assertThat(get("/api/v1/admin/system/status", login("ana@orion.test"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
