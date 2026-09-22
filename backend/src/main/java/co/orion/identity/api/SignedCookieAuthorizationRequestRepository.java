package co.orion.identity.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guarda la solicitud de autorización de Apple en una cookie firmada en vez de en la sesión.
 *
 * <p><strong>Por qué.</strong> Apple devuelve a la persona con un POST desde su dominio
 * ({@code response_mode=form_post}), y la cookie de sesión es SameSite=Lax: en un POST entre
 * sitios el navegador no la manda, la sesión llega vacía y Spring no encuentra la solicitud que
 * él mismo guardó. Esta cookie va con SameSite=None para sobrevivir a ese POST, y por eso mismo va
 * <strong>firmada</strong> (HMAC con una llave que nace con cada arranque) y dura cinco minutos:
 * nadie puede fabricarla ni alterarla, y la de un arranque anterior simplemente no vale.
 *
 * <p>Nunca se deserializa con la serialización de Java: el contenido viene del navegador, y
 * deserializar objetos arbitrarios de una cookie es una puerta a ejecutar código. Es JSON de campos
 * de texto, reconstruido a mano.
 */
class SignedCookieAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE = "ORION_OAUTH2_APPLE";
    private static final Duration VIDA = Duration.ofMinutes(5);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final byte[] llave = new byte[32];
    private final boolean segura;
    private final Clock clock;

    SignedCookieAuthorizationRequestRepository(boolean segura, Clock clock) {
        new SecureRandom().nextBytes(llave);
        this.segura = segura;
        this.clock = clock;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String valor = leer(request);
        if (valor == null) {
            return null;
        }
        int punto = valor.lastIndexOf('.');
        if (punto < 0) {
            return null;
        }
        String cuerpo = valor.substring(0, punto);
        try {
            // Cualquier cosa rara —base64 roto, firma que no cuadra, JSON ajeno— es «no hay
            // solicitud», nunca un 500: la cookie viene del navegador y puede traer lo que sea.
            byte[] firma = Base64.getUrlDecoder().decode(valor.substring(punto + 1));
            if (!MessageDigest.isEqual(firma, hmac(cuerpo))) {
                return null;
            }
            Guardada g = JSON.readValue(Base64.getUrlDecoder().decode(cuerpo), Guardada.class);
            if (clock.instant().isAfter(Instant.ofEpochSecond(g.emitida()).plus(VIDA))) {
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
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest solicitud, HttpServletRequest request,
                                         HttpServletResponse response) {
        if (solicitud == null) {
            borrar(response);
            return;
        }
        Guardada g = new Guardada(solicitud.getAuthorizationUri(), solicitud.getClientId(),
                solicitud.getRedirectUri(), List.copyOf(solicitud.getScopes()), solicitud.getState(),
                comoTexto(solicitud.getAdditionalParameters()), comoTexto(solicitud.getAttributes()),
                solicitud.getAuthorizationRequestUri(), clock.instant().getEpochSecond());
        try {
            String cuerpo = Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(g));
            String valor = cuerpo + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(cuerpo));
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(valor, VIDA).toString());
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo guardar la solicitud de Apple", ex);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest solicitud = loadAuthorizationRequest(request);
        borrar(response);
        return solicitud;
    }

    private void borrar(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    /** SameSite=None exige Secure. En local (http) no hay Apple posible, así que ahí va Lax. */
    private ResponseCookie cookie(String valor, Duration vida) {
        return ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(segura)
                .sameSite(segura ? "None" : "Lax")
                .path("/")
                .maxAge(vida)
                .build();
    }

    private static String leer(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName()) && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private byte[] hmac(String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(llave, "HmacSHA256"));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC no disponible", ex);
        }
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
