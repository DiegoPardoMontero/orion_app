package co.orion.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import jakarta.servlet.http.Cookie;

/**
 * La solicitud viaja en una cookie firmada, una por solicitud: sobrevive a un reinicio del backend
 * —la llave es estable— y a una segunda pestaña. Lo que se prueba es eso y lo que la hace segura:
 * que vuelve idéntica, que alterada no vale y que caduca.
 */
class SignedCookieAuthorizationRequestRepositoryTest {

    private static final Instant AHORA = Instant.parse("2026-09-22T15:00:00Z");

    private static FirmaSocial firma(String secreto) {
        return new FirmaSocial("", secreto, "", "", "");
    }

    private final SignedCookieAuthorizationRequestRepository repo = new SignedCookieAuthorizationRequestRepository(
            firma("secreto-de-google"), true, Clock.fixed(AHORA, ZoneOffset.UTC));

    private static OAuth2AuthorizationRequest solicitud(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://appleid.apple.com/auth/authorize")
                .clientId("co.orion.web")
                .redirectUri("https://orionidiomas.com/login/oauth2/code/apple")
                .scopes(Set.of("openid", "name", "email"))
                .state(state)
                .additionalParameters(p -> p.put("response_mode", "form_post"))
                .attributes(a -> {
                    a.put("registration_id", "apple");
                    a.put("nonce", "n-456");
                })
                .authorizationRequestUri("https://appleid.apple.com/auth/authorize?x=1")
                .build();
    }

    /** Guarda y devuelve el valor de la cookie. */
    private static String guardar(SignedCookieAuthorizationRequestRepository r, String state) {
        MockHttpServletResponse res = new MockHttpServletResponse();
        r.saveAuthorizationRequest(solicitud(state), new MockHttpServletRequest(), res);
        String cookie = res.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith(SignedCookieAuthorizationRequestRepository.nombre(state) + "=")
                .contains("HttpOnly").contains("Secure").contains("SameSite=None");
        return cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    }

    /** El regreso del proveedor: trae el state y las cookies que el navegador tenga. */
    private static MockHttpServletRequest regreso(String state, Cookie... cookies) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login/oauth2/code/apple");
        req.setParameter("state", state);
        req.setCookies(cookies);
        return req;
    }

    private static Cookie cookie(String state, String valor) {
        return new Cookie(SignedCookieAuthorizationRequestRepository.nombre(state), valor);
    }

    @Test
    @DisplayName("Vuelve idéntica: estado, parámetros y atributos (el nonce incluido)")
    void idaYVuelta() {
        OAuth2AuthorizationRequest leida = repo.loadAuthorizationRequest(
                regreso("estado-123", cookie("estado-123", guardar(repo, "estado-123"))));

        assertThat(leida).isNotNull();
        assertThat(leida.getState()).isEqualTo("estado-123");
        assertThat(leida.getClientId()).isEqualTo("co.orion.web");
        assertThat(leida.getScopes()).containsExactlyInAnyOrder("openid", "name", "email");
        assertThat((String) leida.getAttribute("nonce")).isEqualTo("n-456");
        assertThat(leida.getAdditionalParameters()).containsEntry("response_mode", "form_post");
    }

    @Test
    @DisplayName("Sobrevive a un reinicio: otro arranque con los mismos secretos la lee")
    void sobreviveAlReinicio() {
        String valor = guardar(repo, "estado-123");
        SignedCookieAuthorizationRequestRepository despuesDelDespliegue = new SignedCookieAuthorizationRequestRepository(
                firma("secreto-de-google"), true, Clock.fixed(AHORA.plusSeconds(60), ZoneOffset.UTC));

        assertThat(despuesDelDespliegue.loadAuthorizationRequest(regreso("estado-123", cookie("estado-123", valor))))
                .isNotNull();
    }

    @Test
    @DisplayName("Dos pestañas, dos solicitudes: cada regreso encuentra la suya")
    void dosPestanas() {
        Cookie primera = cookie("estado-A", guardar(repo, "estado-A"));
        Cookie segunda = cookie("estado-B", guardar(repo, "estado-B"));

        assertThat(repo.loadAuthorizationRequest(regreso("estado-A", primera, segunda)).getState()).isEqualTo("estado-A");
        assertThat(repo.loadAuthorizationRequest(regreso("estado-B", primera, segunda)).getState()).isEqualTo("estado-B");
    }

    @Test
    @DisplayName("Alterada, rota, de otra llave o de otro state no vale: se lee como que no hay solicitud")
    void alteradaNoVale() {
        String valor = guardar(repo, "estado-123");

        assertThat(repo.loadAuthorizationRequest(regreso("estado-123", cookie("estado-123", "x" + valor.substring(1)))))
                .isNull();
        assertThat(repo.loadAuthorizationRequest(regreso("estado-123", cookie("estado-123", "basura")))).isNull();
        assertThat(repo.loadAuthorizationRequest(regreso("estado-123", cookie("estado-123", "a.@@@")))).isNull();
        assertThat(repo.loadAuthorizationRequest(regreso("otro-state", cookie("otro-state", valor)))).isNull();
        SignedCookieAuthorizationRequestRepository otraLlave = new SignedCookieAuthorizationRequestRepository(
                firma("otro-secreto"), true, Clock.fixed(AHORA, ZoneOffset.UTC));
        assertThat(otraLlave.loadAuthorizationRequest(regreso("estado-123", cookie("estado-123", valor)))).isNull();
    }

    @Test
    @DisplayName("Caduca a los diez minutos, con la misma llave")
    void caduca() {
        Reloj reloj = new Reloj(AHORA);
        SignedCookieAuthorizationRequestRepository conReloj =
                new SignedCookieAuthorizationRequestRepository(firma("secreto-de-google"), true, reloj);
        String valor = guardar(conReloj, "estado-123");

        reloj.ahora = AHORA.plusSeconds(599);
        assertThat(conReloj.loadAuthorizationRequest(regreso("estado-123", cookie("estado-123", valor)))).isNotNull();
        reloj.ahora = AHORA.plusSeconds(601);
        assertThat(conReloj.loadAuthorizationRequest(regreso("estado-123", cookie("estado-123", valor)))).isNull();
    }

    /** Un reloj que se puede adelantar a mano. */
    private static final class Reloj extends Clock {
        Instant ahora;

        Reloj(Instant ahora) {
            this.ahora = ahora;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
