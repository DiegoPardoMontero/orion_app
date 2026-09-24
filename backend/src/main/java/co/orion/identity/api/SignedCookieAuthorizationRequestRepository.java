package co.orion.identity.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guarda la solicitud de autorización en una cookie firmada, y no en la sesión. Para todos los
 * proveedores desde el 24/09/2026 (antes solo Apple).
 *
 * <p><strong>Por qué.</strong> En la sesión en memoria, la solicitud se perdía con cada reinicio o
 * despliegue del backend —quien estaba en la pantalla de Google volvía a «No pudimos entrar»—, y
 * la sesión guarda una sola: un segundo clic o una segunda pestaña pisaban la primera, que volvía
 * sin encontrar la suya. Ahora hay <strong>una cookie por solicitud</strong>, con el nombre sacado
 * de su {@code state}, y la firma usa una llave estable ({@link FirmaSocial}).
 *
 * <p>Apple devuelve a la persona con un POST desde su dominio, y en un POST entre sitios el
 * navegador solo manda cookies SameSite=None: por eso en producción van con None (y Secure). Por
 * eso mismo van firmadas y duran diez minutos.
 */
class SignedCookieAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String PREFIJO = "ORION_OAUTH2_";
    private static final Duration VIDA = Duration.ofMinutes(10);

    private final FirmaSocial firma;
    private final boolean segura;
    private final Clock clock;

    SignedCookieAuthorizationRequestRepository(FirmaSocial firma, boolean segura, Clock clock) {
        this.firma = firma;
        this.segura = segura;
        this.clock = clock;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        Guardada g = firma.desempaquetar(leer(request, nombre(state)), Guardada.class);
        // Firmada, pero de otra solicitud o vencida: tampoco vale.
        if (g == null || !state.equals(g.state())
                || clock.instant().isAfter(Instant.ofEpochSecond(g.emitida()).plus(VIDA))) {
            return null;
        }
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(g.authorizationUri())
                .clientId(g.clientId())
                .redirectUri(g.redirectUri())
                .scopes(Set.copyOf(g.scopes()))
                .state(g.state())
                .additionalParameters(p -> p.putAll(g.additionalParameters()))
                .attributes(a -> a.putAll(g.attributes()))
                .authorizationRequestUri(g.authorizationRequestUri())
                .build();
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest solicitud, HttpServletRequest request,
                                         HttpServletResponse response) {
        if (solicitud == null) {
            String state = request.getParameter(OAuth2ParameterNames.STATE);
            if (state != null) {
                borrar(response, nombre(state));
            }
            return;
        }
        Guardada g = new Guardada(solicitud.getAuthorizationUri(), solicitud.getClientId(),
                solicitud.getRedirectUri(), List.copyOf(solicitud.getScopes()), solicitud.getState(),
                comoTexto(solicitud.getAdditionalParameters()), comoTexto(solicitud.getAttributes()),
                solicitud.getAuthorizationRequestUri(), clock.instant().getEpochSecond());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(nombre(solicitud.getState()), firma.empaquetar(g), VIDA).toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest solicitud = loadAuthorizationRequest(request);
        if (solicitud != null) {
            borrar(response, nombre(solicitud.getState()));
        }
        return solicitud;
    }

    static String nombre(String state) {
        return PREFIJO + FirmaSocial.huella(state);
    }

    private void borrar(HttpServletResponse response, String nombre) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(nombre, "", Duration.ZERO).toString());
    }

    /** SameSite=None exige Secure. En local (http) no hay Apple posible, así que ahí va Lax. */
    private ResponseCookie cookie(String nombre, String valor, Duration vida) {
        return ResponseCookie.from(nombre, valor)
                .httpOnly(true)
                .secure(segura)
                .sameSite(segura ? "None" : "Lax")
                .path("/")
                .maxAge(vida)
                .build();
    }

    private static String leer(HttpServletRequest request, String nombre) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (nombre.equals(c.getName()) && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private static Map<String, String> comoTexto(Map<String, Object> mapa) {
        Map<String, String> texto = new LinkedHashMap<>();
        mapa.forEach((k, v) -> texto.put(k, String.valueOf(v)));
        return texto;
    }

    /** Lo que viaja en la cookie: solo texto. */
    record Guardada(String authorizationUri, String clientId, String redirectUri, List<String> scopes,
                    String state, Map<String, String> additionalParameters, Map<String, String> attributes,
                    String authorizationRequestUri, long emitida) {
    }
}
