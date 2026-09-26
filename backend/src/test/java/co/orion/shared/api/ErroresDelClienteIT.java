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
import org.springframework.http.ResponseEntity;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.api.RegisterRequest;
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

    /**
     * Un solo campo mal, con su mensaje escrito para la persona: ese mensaje es el {@code error}, que
     * es lo único que enseña la pantalla. Antes quedaba en {@code details} y se leía el genérico.
     */
    @Test
    @DisplayName("Un solo campo mal con mensaje propio devuelve ese mensaje")
    @SuppressWarnings("rawtypes")
    void unSoloCampoConMensajePropio() {
        ResponseEntity<Map> r = rest.postForEntity("/api/v1/auth/register", new RegisterRequest(
                "Ana Ramírez", "ana.menor@orion.test", "orion123*", "+573001112233", false, false, true, true),
                Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("error", "Orión está disponible solo para mayores de 18 años.");
    }

    /** Con varios, o con el mensaje por defecto de la anotación (que no dice de qué campo), el genérico. */
    @Test
    @DisplayName("Varios campos mal, o uno sin mensaje propio, devuelven el genérico")
    @SuppressWarnings("rawtypes")
    void variosCamposOUnoSinMensajePropio() {
        ResponseEntity<Map> varios = rest.postForEntity("/api/v1/auth/register", new RegisterRequest(
                "Ana Ramírez", "ana.varios@orion.test", "orion123*", "+573001112233", false, false, false, true),
                Map.class);
        assertThat(varios.getBody()).containsEntry("error", GlobalExceptionHandler.MENSAJE_VALIDACION);

        ResponseEntity<Map> sinMensaje = rest.postForEntity("/api/v1/auth/register", new RegisterRequest(
                "", "ana.sinnombre@orion.test", "orion123*", "+573001112233", false, true, true, true),
                Map.class);
        assertThat(sinMensaje.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sinMensaje.getBody()).containsEntry("error", GlobalExceptionHandler.MENSAJE_VALIDACION);
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
