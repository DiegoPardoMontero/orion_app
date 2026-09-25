package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import co.orion.TestcontainersConfiguration;
import co.orion.identity.application.RegistrationService;
import co.orion.identity.application.SocialLoginService;
import co.orion.identity.application.SocialLoginService.PerfilSocial;
import co.orion.identity.domain.SocialProvider;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;
import co.orion.shared.security.OrionUserDetails;
import co.orion.support.ApiIntegrationSupport;

/**
 * Entrar con Google, Apple o Facebook.
 *
 * <p>Con Google encendido con credenciales de mentira basta para probar el cableado de verdad —la
 * redirección a Google con la dirección de vuelta del frontend— sin tocar la red. Lo que decide qué
 * pasa al volver se prueba contra la base: es ahí donde vive la regla que protege las cuentas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "orion.social.google.client-id=google-de-prueba",
        "orion.social.google.client-secret=secreto-de-prueba",
        "orion.social.microsoft.client-id=microsoft-de-prueba",
        "orion.social.microsoft.client-secret=secreto-de-prueba",
        "orion.app.base-url=https://orion.test"})
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class SocialLoginIT extends ApiIntegrationSupport {

    @Autowired
    private SocialLoginService social;

    @Autowired
    private RegistrationService registro;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PendienteSocial pendientes;

    @BeforeEach
    void limpiar() {
        jdbc.update("delete from social_identities");
        users.deleteAll();
    }

    private static PerfilSocial perfil(String sujeto, String correo, boolean verificado) {
        return new PerfilSocial(SocialProvider.GOOGLE, sujeto, correo, verificado, "Ana Ruiz");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Solo se ofrecen los proveedores configurados en este despliegue")
    void soloLosConfigurados() {
        ResponseEntity<Map> r = rest.getForEntity("/api/v1/auth/social/providers", Map.class);

        assertThat(r.getBody().get("providers")).isEqualTo(List.of("google", "microsoft"));
    }

    @Test
    @DisplayName("La ida lleva a Google con la dirección de vuelta del frontend, no la del backend")
    void laIdaVaAGoogle() throws Exception {
        // Sin seguir la redirección: se mira el 302 tal cual, sin ir a Google de verdad.
        HttpClient cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<Void> r = cliente.send(HttpRequest.newBuilder(
                URI.create(rest.getRootUri() + "/oauth2/authorization/google")).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(r.statusCode()).isEqualTo(302);
        URI destino = URI.create(r.headers().firstValue("Location").orElseThrow());
        assertThat(destino.getHost()).isEqualTo("accounts.google.com");
        Map<String, String> q = UriComponentsBuilder.fromUri(destino).build().getQueryParams()
                .toSingleValueMap();
        assertThat(q.get("client_id")).isEqualTo("google-de-prueba");
        assertThat(URLDecoder.decode(q.get("redirect_uri"), StandardCharsets.UTF_8))
                .isEqualTo("https://orion.test/login/oauth2/code/google");
    }

    @Test
    @DisplayName("La solicitud de Google se guarda en su propia cookie firmada, no en la sesión en memoria")
    void laSolicitudVaEnCookie() throws Exception {
        HttpClient cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<Void> r = cliente.send(HttpRequest.newBuilder(
                URI.create(rest.getRootUri() + "/oauth2/authorization/google")).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        String state = UriComponentsBuilder.fromUriString(r.headers().firstValue("Location").orElseThrow())
                .build().getQueryParams().getFirst("state");
        List<String> cookies = r.headers().allValues("Set-Cookie");
        // Un reinicio del backend mientras la persona está en Google ya no pierde su regreso.
        assertThat(cookies).anySatisfy(c -> assertThat(c)
                .startsWith(SignedCookieAuthorizationRequestRepository.nombre(URLDecoder.decode(state, StandardCharsets.UTF_8)) + "=")
                .contains("HttpOnly"));
        assertThat(cookies).noneSatisfy(c -> assertThat(c).startsWith("ORION_SESSION="));
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("El alta a medio completar vive en una cookie firmada: otro arranque la lee; una falsa, no")
    void elPendienteEnCookie() {
        MockHttpServletResponse guardado = new MockHttpServletResponse();
        pendientes.guardar(perfil("g-9", "nueva@orion.test", true), guardado);
        String cookie = guardado.getHeader(HttpHeaders.SET_COOKIE);
        String valor = cookie.substring(0, cookie.indexOf(';'));

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, valor);
        ResponseEntity<Map> r = rest.exchange("/api/v1/auth/social/pending", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("email", "nueva@orion.test").containsEntry("provider", "google");

        HttpHeaders falsa = new HttpHeaders();
        falsa.add(HttpHeaders.COOKIE, PendienteSocial.COOKIE + "=eyJwcm92ZWVkb3IiOiJHT09HTEUifQ.Zm9v");
        assertThat(rest.exchange("/api/v1/auth/social/pending", HttpMethod.GET, new HttpEntity<>(falsa), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Cada fallo dice su motivo: cancelar en Google no es lo mismo que volver tarde")
    void motivos() {
        assertThat(SocialLogin.motivo(new OAuth2AuthenticationException(new OAuth2Error("access_denied"))))
                .isEqualTo("cancelado");
        assertThat(SocialLogin.motivo(new OAuth2AuthenticationException(
                new OAuth2Error("authorization_request_not_found")))).isEqualTo("vencido");
        assertThat(SocialLogin.motivo(new IllegalStateException("otra cosa"))).isEqualTo("error");
    }

    @Test
    @DisplayName("Alguien nuevo no entra sin completar: la cuenta no nace sin sus casillas")
    void alguienNuevoCompleta() {
        assertThat(social.resolver(perfil("g-1", "nueva@orion.test", true)))
                .isInstanceOf(SocialLoginService.Completa.class);
        assertThat(users.findByEmailIgnoreCase("nueva@orion.test")).isEmpty();
    }

    @Test
    @DisplayName("Con un correo que ya existe y el proveedor lo verificó, se vincula y entra")
    void seVinculaSiElCorreoEstaVerificado() {
        User ana = createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);

        SocialLoginService.Resultado r = social.resolver(perfil("g-2", "ana@orion.test", true));

        assertThat(r).isInstanceOf(SocialLoginService.Entra.class);
        assertThat(((SocialLoginService.Entra) r).user().getId()).isEqualTo(ana.getId());
        // Y la próxima vez entra por la identidad, aunque el correo de Google haya cambiado.
        assertThat(social.resolver(perfil("g-2", "otro@gmail.com", true)))
                .isInstanceOf(SocialLoginService.Entra.class);
    }

    @Test
    @DisplayName("Una cuenta preparada con el correo de otro pasa a su dueña: la contraseña del intruso deja de servir")
    void laCuentaPreparadaPasaASuDuena() {
        // Alguien registró ana@ con una contraseña suya y nunca pudo confirmar el correo.
        users.save(new User("ana@orion.test", passwordEncoder.encode(PASSWORD), "Intruso", UserRole.STUDENT));

        SocialLoginService.Resultado r = social.resolver(perfil("g-6", "ana@orion.test", true));

        assertThat(r).isInstanceOf(SocialLoginService.Entra.class);
        User cuenta = users.findByEmailIgnoreCase("ana@orion.test").orElseThrow();
        assertThat(cuenta.isEmailVerified()).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, cuenta.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("Una cuenta ya verificada conserva su contraseña al vincular: nadie la preparó")
    void laVerificadaConservaSuContrasena() {
        createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);

        social.resolver(perfil("g-7", "ana@orion.test", true));

        assertThat(passwordEncoder.matches(PASSWORD,
                users.findByEmailIgnoreCase("ana@orion.test").orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Con un correo que ya existe pero sin verificar no se vincula: sería quedarse con una cuenta ajena")
    void sinVerificarNoSeVincula() {
        createUser("ana@orion.test", "Ana Ruiz", UserRole.STUDENT);

        SocialLoginService.Resultado r = social.resolver(perfil("g-3", "ana@orion.test", false));

        assertThat(r).isEqualTo(new SocialLoginService.Rechazado("correo-sin-verificar"));
        assertThat(jdbc.queryForObject("select count(*) from social_identities", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sin correo del proveedor no hay cuenta posible, y se dice por qué")
    void sinCorreo() {
        assertThat(social.resolver(perfil("g-4", null, false)))
                .isEqualTo(new SocialLoginService.Rechazado("sin-correo"));
    }

    @Test
    @DisplayName("La ida lleva a Microsoft, al extremo de cuentas personales y de trabajo, con la vuelta del frontend")
    void laIdaVaAMicrosoft() throws Exception {
        HttpClient cliente = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<Void> r = cliente.send(HttpRequest.newBuilder(
                URI.create(rest.getRootUri() + "/oauth2/authorization/microsoft")).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(r.statusCode()).isEqualTo(302);
        URI destino = URI.create(r.headers().firstValue("Location").orElseThrow());
        assertThat(destino.getHost()).isEqualTo("login.microsoftonline.com");
        assertThat(destino.getPath()).isEqualTo("/common/oauth2/v2.0/authorize");
        Map<String, String> q = UriComponentsBuilder.fromUri(destino).build().getQueryParams().toSingleValueMap();
        assertThat(q.get("client_id")).isEqualTo("microsoft-de-prueba");
        assertThat(URLDecoder.decode(q.get("redirect_uri"), StandardCharsets.UTF_8))
                .isEqualTo("https://orion.test/login/oauth2/code/microsoft");
    }

    @Test
    @DisplayName("Con Microsoft la cuenta nace sin verificar y queda vinculada: a la segunda, entra")
    void completarConMicrosoft() {
        PerfilSocial deMicrosoft = new PerfilSocial(SocialProvider.MICROSOFT, "ms-1", "ana@outlook.com", false, "Ana Ruiz");

        User creada = social.completar(deMicrosoft, "Ana Ruiz", "+573001112244", false, registro);

        assertThat(creada.isEmailVerified()).isFalse();
        assertThat(jdbc.queryForObject("select provider from social_identities", String.class)).isEqualTo("MICROSOFT");
        assertThat(social.resolver(deMicrosoft)).isInstanceOf(SocialLoginService.Entra.class);
    }

    @Test
    @DisplayName("Completar crea la cuenta de estudiante, verificada si el proveedor lo garantizó")
    void completarCreaLaCuenta() {
        User creada = social.completar(perfil("g-5", "nueva@orion.test", true), "Ana Ruiz", "+573001112244", false, registro);

        assertThat(creada.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(creada.isEmailVerified()).isTrue();
        // Y la pantalla lo sabe: «Cambiar contraseña» se vuelve «Crear una».
        assertThat(UserResponse.from(new OrionUserDetails(creada)).hasPassword()).isFalse();
        // Sin contraseña utilizable: el login con correo no abre esta cuenta.
        ResponseEntity<String> conClave = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", "nueva@orion.test", "password", "cualquiera"), String.class);
        assertThat(conClave.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(social.resolver(perfil("g-5", "nueva@orion.test", true)))
                .isInstanceOf(SocialLoginService.Entra.class);
    }

    /**
     * Desde «Quiero enseñar» también hay «Continuar con Google» (Pardo, 23/09/2026): la cuenta nace
     * como con contraseña —de estudiante, con la intención de enseñar— y por eso llega como aspirante
     * y aterriza en su postulación, no en el buscador.
     */
    @Test
    @DisplayName("Completar desde «Quiero enseñar» crea un aspirante a profesor, como el alta con contraseña")
    void completarParaEnsenar() {
        User creada = social.completar(perfil("g-6", "profe@orion.test", true), "María Gómez", "+573001112244", true, registro);

        assertThat(creada.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(UserResponse.from(new OrionUserDetails(creada)).role()).isEqualTo("TEACHER_APPLICANT");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Completar sin haber pasado por el proveedor responde 404, no crea nada")
    void completarSinPendiente() {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, "XSRF-TOKEN=token-de-prueba");
        h.add("X-XSRF-TOKEN", "token-de-prueba");
        ResponseEntity<Map> r = rest.postForEntity("/api/v1/auth/social/complete", new HttpEntity<>(
                Map.of("fullName", "Ana", "whatsappPhone", "+573001112244",
                        "adult", true, "acceptsTerms", true, "acceptsDataPolicy", true), h),
                Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Google no trae el número, y el WhatsApp es obligatorio (Pardo, 25/09): sin él no se crea nada. */
    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Completar sin WhatsApp, o con uno que no sirve, responde 400")
    void completarSinWhatsapp() {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, "XSRF-TOKEN=token-de-prueba");
        h.add("X-XSRF-TOKEN", "token-de-prueba");
        for (String numero : new String[] {"", "+57601234"}) {
            ResponseEntity<Map> r = rest.postForEntity("/api/v1/auth/social/complete", new HttpEntity<>(
                    Map.of("fullName", "Ana", "whatsappPhone", numero,
                            "adult", true, "acceptsTerms", true, "acceptsDataPolicy", true), h),
                    Map.class);

            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Completar exige el token CSRF: crea una cuenta y abre sesión, no es una puerta lateral")
    void completarExigeCsrf() {
        ResponseEntity<Map> r = rest.postForEntity("/api/v1/auth/social/complete",
                Map.of("fullName", "Ana", "adult", true, "acceptsTerms", true, "acceptsDataPolicy", true),
                Map.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
