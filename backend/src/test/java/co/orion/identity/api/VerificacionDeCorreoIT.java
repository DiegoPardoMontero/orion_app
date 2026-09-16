package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.orion.identity.application.EmailVerificationMailer;
import co.orion.identity.domain.SignupIntent;
import co.orion.support.ApiIntegrationSupport;
import co.orion.TestcontainersConfiguration;

/**
 * La verificación de correo, de punta a punta.
 *
 * <p>El mailer se sustituye por uno que guarda los enlaces: el token en claro solo existe dentro
 * del correo —en la base vive su hash— así que capturarlo ahí es la única forma de probar el flujo
 * sin levantar SMTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({TestcontainersConfiguration.class, VerificacionDeCorreoIT.Config.class})
class VerificacionDeCorreoIT extends ApiIntegrationSupport {

    /** Captura los enlaces enviados. Registrado como @Primary para desplazar al real. */
    static class MailerEspia implements EmailVerificationMailer {
        final List<String> enlaces = new ArrayList<>();
        final List<SignupIntent> intenciones = new ArrayList<>();

        @Override
        public void sendVerificationLink(String toEmail, String fullName, String link,
                                         SignupIntent intent) {
            enlaces.add(link);
            intenciones.add(intent);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MailerEspia mailerEspia() {
            return new MailerEspia();
        }
    }

    @Autowired
    private MailerEspia mailer;

    private static final String CORREO = "camila@orion.test";
    private static final String CLAVE = "orion123*";

    @BeforeEach
    void limpiar() {
        // La superclase limpia las tablas dependientes; los usuarios los borra cada IT, porque es
        // cada IT quien sabe qué sembró. Sin esto, el segundo test se registra sobre el primero y
        // recibe un 409 en vez de un alta.
        studentProfiles.deleteAll();
        users.deleteAll();
        mailer.enlaces.clear();
        mailer.intenciones.clear();
    }

    private ResponseEntity<Map> registrar(String email) {
        return registrar(email, false);
    }

    private ResponseEntity<Map> registrar(String email, boolean quiereEnsenar) {
        return rest.postForEntity("/api/v1/auth/register",
                new RegisterRequest("Camila Ortiz", email, CLAVE, null, quiereEnsenar,
                        true, true, true),
                Map.class);
    }

    private String tokenDelUltimoEnlace() {
        String enlace = mailer.enlaces.get(mailer.enlaces.size() - 1);
        return enlace.substring(enlace.indexOf("token=") + "token=".length());
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Registrarse manda el enlace y deja la cuenta sin verificar")
    void registrarseMandaElEnlace() {
        ResponseEntity<Map> respuesta = registrar(CORREO);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).containsEntry("emailVerified", false);
        assertThat(mailer.enlaces).hasSize(1);
        assertThat(mailer.enlaces.getFirst()).contains("/verificar?token=");
    }

    @Test
    @DisplayName("El correo de verificación sabe por qué puerta entró la cuenta")
    void elCorreoDistingueAlQueVieneAEnsenar() {
        registrar(CORREO, true);
        assertThat(mailer.intenciones).containsExactly(SignupIntent.TEACH);

        // Y el caso corriente sigue siendo el del estudiante, que es el texto de siempre.
        mailer.intenciones.clear();
        registrar("otra@orion.test", false);
        assertThat(mailer.intenciones).containsExactly(SignupIntent.LEARN);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El enlace verifica el correo, y no hace falta tener sesión abierta")
    void elEnlaceVerificaSinSesion() {
        registrar(CORREO);

        // Sin cookie: quien llega desde su buzón puede estar en otro navegador.
        ResponseEntity<Void> verificacion = rest.postForEntity("/api/v1/auth/verify-email",
                new VerifyEmailRequest(tokenDelUltimoEnlace()), Void.class);

        assertThat(verificacion.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(users.findByEmailIgnoreCase(CORREO).orElseThrow().isEmailVerified()).isTrue();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un token ya usado no vuelve a servir")
    void elTokenEsDeUnSoloUso() {
        registrar(CORREO);
        String token = tokenDelUltimoEnlace();
        rest.postForEntity("/api/v1/auth/verify-email", new VerifyEmailRequest(token), Void.class);

        ResponseEntity<Map> segunda = rest.postForEntity("/api/v1/auth/verify-email",
                new VerifyEmailRequest(token), Map.class);

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Un token inventado no verifica nada")
    void unTokenInventadoNoSirve() {
        registrar(CORREO);

        ResponseEntity<Map> respuesta = rest.postForEntity("/api/v1/auth/verify-email",
                new VerifyEmailRequest("no-soy-un-token"), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(users.findByEmailIgnoreCase(CORREO).orElseThrow().isEmailVerified()).isFalse();
    }

    /**
     * El freno del reenvío. Sin él, el botón es un altavoz apuntando al buzón de cualquiera cuya
     * dirección se conozca.
     */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El reenvío se corta al cuarto intento en la misma hora")
    void elReenvioTieneFreno() {
        registrar(CORREO);
        Session sesion = login(CORREO);
        String reenvio = "/api/v1/me/account/email-verification/resend";

        // El alta ya gastó uno de los tres; quedan dos.
        assertThat(post(reenvio, sesion, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post(reenvio, sesion, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> cuarto = post(reenvio, sesion, null, Map.class);

        assertThat(cuarto.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mailer.enlaces).hasSize(3);
    }

    /** Reservar es lo único que la verificación cierra. Buscar sigue abierto. */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Sin verificar no se puede reservar, pero sí buscar profesores")
    void sinVerificarNoSeReservaPeroSiSeBusca() {
        registrar(CORREO);
        Session sesion = login(CORREO);

        assertThat(get("/api/v1/professors", sesion, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> reserva = post("/api/v1/bookings", sesion, Map.of(
                "professorId", java.util.UUID.randomUUID().toString(),
                "startsAt", "2026-12-01T18:00:00Z",
                "modality", "VIRTUAL"), Map.class);

        assertThat(reserva.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(String.valueOf(reserva.getBody().get("error"))).contains("Confirma tu correo");
    }
}
