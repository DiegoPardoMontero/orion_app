package co.orion.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import co.orion.TestcontainersConfiguration;
import co.orion.support.ApiIntegrationSupport;

/**
 * Lo que un cliente manda mal es un 4xx, nunca un 500. No es cosmética: cada 500 manda un correo de
 * alerta, y un 500 que cualquiera provoca desde el navegador es una forma gratis de llenar la
 * bandeja de alertas hasta que nadie las lea.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ErroresDelClienteIT extends ApiIntegrationSupport {

    @Test
    @DisplayName("Un id que no es UUID en la ruta es 400")
    void idQueNoEsUuid() {
        assertThat(rest.getForEntity("/api/v1/professors/hola", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Un parámetro obligatorio que falta es 400")
    void faltaUnParametro() {
        assertThat(rest.getForEntity("/api/v1/auth/invite", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Una contraseña de más de 72 bytes es 400 en el login, aunque tenga menos de 72 caracteres")
    void contrasenaQueBcryptNoAcepta() {
        String cuarentaEmojis = "🔑".repeat(40);

        assertThat(rest.postForEntity("/api/v1/auth/login",
                Map.of("email", "ana@orion.test", "password", cuarentaEmojis), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON es 415")
    void cuerpoQueNoEsJson() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.TEXT_PLAIN);

        assertThat(rest.postForEntity("/api/v1/auth/login", new HttpEntity<>("hola", h), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
}
