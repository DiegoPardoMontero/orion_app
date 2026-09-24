package co.orion.identity.api;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.identity.application.SocialLoginService;
import co.orion.identity.application.SocialLoginService.PerfilSocial;
import co.orion.identity.application.SocialProviders;
import co.orion.identity.domain.SocialProvider;
import co.orion.identity.domain.User;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Entrar con Google, Apple o Facebook, enchufado a la cadena de seguridad.
 *
 * <p><strong>La sesión que queda es la de siempre.</strong> Spring deja en el contexto un
 * {@code OAuth2AuthenticationToken}, pero toda la aplicación —{@code /auth/me},
 * {@code FreshPrincipalFilter}, cada {@code @AuthenticationPrincipal}— espera un
 * {@link OrionUserDetails}. Al volver del proveedor se sustituye por uno, igual que hace el login con
 * contraseña, y a partir de ahí nadie distingue cómo entró la persona.
 *
 * <p>Las tres salidas de {@link SocialLoginService} terminan en una redirección al frontend: a
 * {@code /entrar/listo} si entra, a {@code /registro/completar} si es alguien nuevo que tiene que
 * marcar las casillas, y a {@code /login?social=motivo} si no puede entrar.
 *
 * <p>Apple tiene dos particularidades que viven aquí: vuelve con un POST
 * ({@code response_mode=form_post}), y su secreto es un JWT que se firma en cada intercambio.
 */
@Component
public class SocialLogin {

    private static final Logger log = LoggerFactory.getLogger(SocialLogin.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SocialProviders proveedores;
    private final SocialLoginService servicio;
    private final String baseUrl;
    private final SecurityContextRepository contextos = new HttpSessionSecurityContextRepository();
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> solicitudes;
    private final PendienteSocial pendientes;

    SocialLogin(SocialProviders proveedores,
                SocialLoginService servicio,
                FirmaSocial firma,
                PendienteSocial pendientes,
                @Value("${orion.app.base-url}") String baseUrl,
                @Value("${server.servlet.session.cookie.secure:false}") boolean segura,
                Clock clock) {
        this.proveedores = proveedores;
        this.servicio = servicio;
        this.baseUrl = baseUrl;
        this.pendientes = pendientes;
        this.solicitudes = new SignedCookieAuthorizationRequestRepository(firma, segura, clock);
    }

    public boolean encendido() {
        return proveedores.algunoConfigurado();
    }

    /** Lo que SecurityConfig enchufa, solo si hay algún proveedor configurado. */
    public void configurar(HttpSecurity http) throws Exception {
        RestClientAuthorizationCodeTokenResponseClient tokens =
                new RestClientAuthorizationCodeTokenResponseClient();
        // Con tiempos: sin ellos, un proveedor lento dejaba la petición colgada hasta que el proxy de
        // Next la cortaba a los 30 s con un «Internal Server Error» pelado.
        tokens.setRestClient(RestClient.builder()
                .requestFactory(conTiempos())
                .messageConverters(c -> {
                    c.clear();
                    c.add(new FormHttpMessageConverter());
                    c.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
        tokens.setParametersCustomizer(parametros -> {
            if (proveedores.appleConfigurado()
                    && proveedores.appleClientId().equals(parametros.getFirst(OAuth2ParameterNames.CLIENT_ID))) {
                parametros.set(OAuth2ParameterNames.CLIENT_SECRET, proveedores.secretoDeApple());
            }
        });

        http.oauth2Login(o -> o
                .clientRegistrationRepository(proveedores)
                .authorizationEndpoint(a -> a
                        .authorizationRequestResolver(resolvedor())
                        .authorizationRequestRepository(solicitudes))
                .tokenEndpoint(t -> t.accessTokenResponseClient(tokens))
                .successHandler(this::alVolver)
                .failureHandler((req, res, ex) -> {
                    String motivo = motivo(ex);
                    log.warn("El login social falló ({}): {}", motivo, ex.getMessage());
                    res.sendRedirect(baseUrl + "/login?social=" + motivo);
                }));
    }

    /**
     * El porqué, con nombre (24/09/2026): antes todo terminaba en el mismo «No pudimos entrar», y ni
     * la persona sabía qué hacer ni el registro decía qué había pasado.
     */
    static String motivo(Exception ex) {
        if (ex instanceof OAuth2AuthenticationException oauth) {
            String codigo = oauth.getError().getErrorCode();
            if ("access_denied".equals(codigo)) {
                return "cancelado";
            }
            if ("authorization_request_not_found".equals(codigo) || "invalid_state_parameter".equals(codigo)) {
                return "vencido";
            }
        }
        return "error";
    }

    private static JdkClientHttpRequestFactory conTiempos() {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(15));
        return fabrica;
    }

    /** Apple exige {@code response_mode=form_post} cuando se piden el nombre y el correo. */
    private OAuth2AuthorizationRequestResolver resolvedor() {
        DefaultOAuth2AuthorizationRequestResolver base =
                new DefaultOAuth2AuthorizationRequestResolver(proveedores, "/oauth2/authorization");
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return paraApple(base.resolve(request));
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
                return paraApple(base.resolve(request, registrationId));
            }
        };
    }

    private static OAuth2AuthorizationRequest paraApple(OAuth2AuthorizationRequest solicitud) {
        if (solicitud == null || !SocialProviders.APPLE.equals(
                solicitud.getAttribute(OAuth2ParameterNames.REGISTRATION_ID))) {
            return solicitud;
        }
        return OAuth2AuthorizationRequest.from(solicitud)
                .additionalParameters(p -> p.put("response_mode", "form_post"))
                .build();
    }

    private void alVolver(HttpServletRequest request, HttpServletResponse response,
                          Authentication autenticacion) throws IOException {
        try {
            decidir(request, response, (OAuth2AuthenticationToken) autenticacion);
        } catch (RuntimeException ex) {
            // Una carrera al vincular (dos regresos a la vez) o la base caída: a la pantalla de entrar
            // con su motivo, nunca un 500 pelado a mitad del regreso.
            log.error("El login social falló después de volver del proveedor", ex);
            cerrarSesion(request, response);
            response.sendRedirect(baseUrl + "/login?social=error");
        }
    }

    private void decidir(HttpServletRequest request, HttpServletResponse response,
                         OAuth2AuthenticationToken token) throws IOException {
        PerfilSocial perfil = perfil(token.getAuthorizedClientRegistrationId(), token.getPrincipal(), request);

        switch (servicio.resolver(perfil)) {
            case SocialLoginService.Entra entra -> {
                pendientes.borrar(response);
                abrirSesion(entra.user(), request, response);
                response.sendRedirect(baseUrl + "/entrar/listo");
            }
            case SocialLoginService.Completa completa -> {
                cerrarSesion(request, response);
                pendientes.guardar(completa.perfil(), response);
                response.sendRedirect(baseUrl + "/registro/completar");
            }
            case SocialLoginService.Rechazado rechazo -> {
                cerrarSesion(request, response);
                response.sendRedirect(baseUrl + "/login?social=" + rechazo.motivo());
            }
        }
    }

    /** Lo mismo que hace el login con contraseña: el principal de toda la aplicación, en la sesión. */
    void abrirSesion(User user, HttpServletRequest request, HttpServletResponse response) {
        OrionUserDetails detalles = new OrionUserDetails(user);
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                detalles, null, detalles.getAuthorities()));
        SecurityContextHolder.setContext(contexto);
        // Como en el login con contraseña: id de sesión nuevo al autenticarse.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        contextos.saveContext(contexto, request, response);
    }

    private void cerrarSesion(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext vacio = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(vacio);
        contextos.saveContext(vacio, request, response);
    }

    /**
     * Lo que dice cada proveedor, traducido a lo mismo. Google y Apple son OpenID Connect y dicen si
     * el correo está verificado; Facebook solo entrega el correo principal, que ya exige confirmado.
     * Apple manda el nombre una sola vez, la primera, y no en el token sino en el parámetro
     * {@code user} de ese mismo POST.
     */
    static PerfilSocial perfil(String registro, OAuth2User usuario, HttpServletRequest request) {
        SocialProvider proveedor = SocialProvider.fromRegistrationId(registro);
        Map<String, Object> a = usuario.getAttributes();
        return switch (proveedor) {
            case GOOGLE -> {
                OidcUser oidc = (OidcUser) usuario;
                yield new PerfilSocial(proveedor, oidc.getSubject(), oidc.getEmail(),
                        Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getFullName());
            }
            case FACEBOOK -> new PerfilSocial(proveedor, String.valueOf(a.get("id")),
                    (String) a.get("email"), a.get("email") != null, (String) a.get("name"));
            case APPLE -> {
                OidcUser oidc = (OidcUser) usuario;
                Object verificado = a.get("email_verified");
                yield new PerfilSocial(proveedor, oidc.getSubject(), oidc.getEmail(),
                        "true".equals(String.valueOf(verificado)), nombreDeApple(request));
            }
            // Microsoft no garantiza que el correo esté verificado (en cuentas de trabajo lo pone
            // quien administra el directorio), así que nunca cuenta como verificado: una cuenta
            // nueva recibe el correo de verificación y una existente no se vincula sola. Sin
            // «email», el nombre de usuario, si tiene forma de correo.
            case MICROSOFT -> {
                OidcUser oidc = (OidcUser) usuario;
                String correo = oidc.getEmail() != null ? oidc.getEmail() : comoCorreo(oidc.getPreferredUsername());
                yield new PerfilSocial(proveedor, oidc.getSubject(), correo, false, oidc.getFullName());
            }
        };
    }

    private static String comoCorreo(String valor) {
        return valor != null && valor.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") ? valor : null;
    }

    private static String nombreDeApple(HttpServletRequest request) {
        String user = request.getParameter("user");
        if (user == null || user.isBlank()) {
            return null;
        }
        try {
            JsonNode nombre = JSON.readTree(user).path("name");
            String completo = (nombre.path("firstName").asText("") + " "
                    + nombre.path("lastName").asText("")).trim();
            return completo.isEmpty() ? null : completo;
        } catch (Exception ex) {
            return null;
        }
    }
}
