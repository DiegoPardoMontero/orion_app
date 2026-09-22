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
 * La solicitud de Apple viaja en una cookie porque su vuelta por POST no trae la de sesión. Lo que
 * se prueba es lo que la hace segura: que vuelve idéntica, que alterada no vale y que caduca.
 */
class SignedCookieAuthorizationRequestRepositoryTest {

    private static final Instant AHORA = Instant.parse("2026-09-22T15:00:00Z");

    private final SignedCookieAuthorizationRequestRepository repo =
            new SignedCookieAuthorizationRequestRepository(true, Clock.fixed(AHORA, ZoneOffset.UTC));

    private OAuth2AuthorizationRequest solicitud() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://appleid.apple.com/auth/authorize")
                .clientId("co.orion.web")
                .redirectUri("https://orionidiomas.com/login/oauth2/code/apple")
                .scopes(Set.of("openid", "name", "email"))
                .state("estado-123")
                .additionalParameters(p -> p.put("response_mode", "form_post"))
                .attributes(a -> {
                    a.put("registration_id", "apple");
                    a.put("nonce", "n-456");
                })
                .authorizationRequestUri("https://appleid.apple.com/auth/authorize?x=1")
                .build();
    }

    private String guardar() {
        MockHttpServletResponse res = new MockHttpServletResponse();
        repo.saveAuthorizationRequest(solicitud(), new MockHttpServletRequest(), res);
        String cookie = res.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=None");
        return cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    }

    private MockHttpServletRequest conCookie(String valor) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login/oauth2/code/apple");
        req.setCookies(new Cookie(SignedCookieAuthorizationRequestRepository.COOKIE, valor));
        return req;
    }

    @Test
    @DisplayName("Vuelve idéntica: estado, parámetros y atributos (el nonce incluido)")
    void idaYVuelta() {
        OAuth2AuthorizationRequest leida = repo.loadAuthorizationRequest(conCookie(guardar()));

        assertThat(leida).isNotNull();
        assertThat(leida.getState()).isEqualTo("estado-123");
        assertThat(leida.getClientId()).isEqualTo("co.orion.web");
        assertThat(leida.getScopes()).containsExactlyInAnyOrder("openid", "name", "email");
        assertThat((String) leida.getAttribute("nonce")).isEqualTo("n-456");
        assertThat(leida.getAdditionalParameters()).containsEntry("response_mode", "form_post");
    }

    @Test
    @DisplayName("Alterada, rota o de otro arranque no vale: se lee como que no hay solicitud")
    void alteradaNoVale() {
        String valor = guardar();
        String alterada = "x" + valor.substring(1);

        assertThat(repo.loadAuthorizationRequest(conCookie(alterada))).isNull();
        assertThat(repo.loadAuthorizationRequest(conCookie("basura"))).isNull();
        assertThat(repo.loadAuthorizationRequest(conCookie("a.@@@"))).isNull();
        // Otro arranque, otra llave.
        SignedCookieAuthorizationRequestRepository otro =
                new SignedCookieAuthorizationRequestRepository(true, Clock.fixed(AHORA, ZoneOffset.UTC));
        assertThat(otro.loadAuthorizationRequest(conCookie(valor))).isNull();
    }

    @Test
    @DisplayName("Caduca a los cinco minutos, con la misma llave")
    void caduca() {
        Reloj reloj = new Reloj(AHORA);
        SignedCookieAuthorizationRequestRepository conReloj =
                new SignedCookieAuthorizationRequestRepository(true, reloj);
        MockHttpServletResponse res = new MockHttpServletResponse();
        conReloj.saveAuthorizationRequest(solicitud(), new MockHttpServletRequest(), res);
        String cookie = res.getHeader(HttpHeaders.SET_COOKIE);
        String valor = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));

        reloj.ahora = AHORA.plusSeconds(299);
        assertThat(conReloj.loadAuthorizationRequest(conCookie(valor))).isNotNull();
        reloj.ahora = AHORA.plusSeconds(301);
        assertThat(conReloj.loadAuthorizationRequest(conCookie(valor))).isNull();
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
