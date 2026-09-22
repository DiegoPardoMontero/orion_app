package co.orion.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.domain.UserRole;
import co.orion.shared.security.IntentosDeAcceso;
import co.orion.support.application.CallbackRequestMailer;
import co.orion.support.domain.CallbackRequest;

/**
 * «¿Prefieres que te llame una persona?»: sin cuenta se pide, la academia se entera y el admin lo
 * marca atendido. Y sin la autorización no se guarda ningún número.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, CallbackRequestIT.AvisoCapturado.class})
class CallbackRequestIT extends ApiIntegrationSupport {

    @TestConfiguration
    static class AvisoCapturado {
        @Bean
        @Primary
        Capturador capturador() {
            return new Capturador();
        }
    }

    static class Capturador implements CallbackRequestMailer {
        volatile CallbackRequest ultimo;

        @Override
        public void avisar(CallbackRequest solicitud) {
            this.ultimo = solicitud;
        }
    }

    @Autowired
    private Capturador aviso;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private IntentosDeAcceso intentos;

    @BeforeEach
    void limpiar() {
        jdbc.update("delete from callback_requests");
        users.deleteAll();
        intentos.olvidarTodo();
        aviso.ultimo = null;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> pedir(Map<String, Object> cuerpo) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.COOKIE, "XSRF-TOKEN=csrf");
        h.add("X-XSRF-TOKEN", "csrf");
        return rest.exchange("/api/v1/callback-requests", HttpMethod.POST, new HttpEntity<>(cuerpo, h),
                Map.class);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin cuenta se pide, el número queda limpio y la academia recibe el aviso")
    void sePideSinCuenta() {
        ResponseEntity<Map> r = pedir(Map.of("firstName", "Lucía", "whatsapp", "+57 300 123-4567",
                "acceptsContact", true));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(aviso.ultimo).isNotNull();
        assertThat(aviso.ultimo.getWhatsapp()).isEqualTo("+573001234567");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin la autorización, o con un número imposible, no se guarda nada")
    void sinAutorizacionNoSeGuarda() {
        ResponseEntity<Map> sinPermiso = pedir(Map.of("firstName", "Lucía", "whatsapp", "3001234567",
                "acceptsContact", false));
        ResponseEntity<Map> numeroCorto = pedir(Map.of("firstName", "Lucía", "whatsapp", "123",
                "acceptsContact", true));

        assertThat(sinPermiso.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(numeroCorto.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(jdbc.queryForObject("select count(*) from callback_requests", Integer.class)).isZero();
    }

    @Test
    @DisplayName("El admin lo ve pendiente arriba, lo atiende, y un estudiante no ve la bandeja")
    void elAdminLoAtiende() {
        pedir(Map.of("firstName", "Lucía", "whatsapp", "3001234567", "acceptsContact", true));
        createUser("admin@orion.test", "Admin", UserRole.ADMIN);
        createUser("ana@orion.test", "Ana", UserRole.STUDENT);
        Session admin = login("admin@orion.test");
        Session ana = login("ana@orion.test");

        List<?> bandeja = get("/api/v1/admin/callback-requests", admin, List.class).getBody();
        assertThat(bandeja).hasSize(1);
        String id = (String) ((Map<?, ?>) bandeja.getFirst()).get("id");
        assertThat(((Map<?, ?>) bandeja.getFirst()).get("attendedAt")).isNull();

        Map<?, ?> atendida = post("/api/v1/admin/callback-requests/" + id + "/attend", admin,
                Map.of(), Map.class).getBody();
        assertThat(atendida.get("attendedAt")).isNotNull();

        assertThat(get("/api/v1/admin/callback-requests", ana, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
