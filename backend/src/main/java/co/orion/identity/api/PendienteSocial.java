package co.orion.identity.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import co.orion.identity.application.SocialLoginService.PerfilSocial;
import co.orion.identity.domain.SocialProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Quien vuelve nuevo del proveedor y todavía no marcó las casillas del alta. Vive en una cookie
 * firmada de media hora, y no en la sesión en memoria (24/09/2026): un despliegue en medio del
 * alta ya no deja a la persona con «Tu ingreso venció».
 *
 * <p>Es solo lo que el proveedor ya dijo de la persona —quién es para él, su correo, su nombre y
 * si el correo está verificado—, firmado: nadie puede fabricar una ni cambiarle el correo.
 */
@Component
class PendienteSocial {

    static final String COOKIE = "ORION_SOCIAL_PENDIENTE";
    private static final Duration VIDA = Duration.ofMinutes(30);

    private final FirmaSocial firma;
    private final boolean segura;
    private final Clock clock;

    PendienteSocial(FirmaSocial firma, @Value("${server.servlet.session.cookie.secure:false}") boolean segura,
                    Clock clock) {
        this.firma = firma;
        this.segura = segura;
        this.clock = clock;
    }

    void guardar(PerfilSocial perfil, HttpServletResponse response) {
        Guardado g = new Guardado(perfil.proveedor().name(), perfil.sujeto(), perfil.correo(),
                perfil.correoVerificado(), perfil.nombre(), clock.instant().getEpochSecond());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(firma.empaquetar(g), VIDA).toString());
    }

    /** El pendiente, si hay uno vigente y bien firmado; si no, {@code null}. */
    PerfilSocial leer(HttpServletRequest request) {
        Guardado g = firma.desempaquetar(valor(request), Guardado.class);
        if (g == null || clock.instant().isAfter(Instant.ofEpochSecond(g.emitido()).plus(VIDA))) {
            return null;
        }
        try {
            return new PerfilSocial(SocialProvider.valueOf(g.proveedor()), g.sujeto(), g.correo(),
                    g.correoVerificado(), g.nombre());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    void borrar(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    /** Lax: se lee en peticiones de la propia app, nunca en un regreso entre sitios. */
    private ResponseCookie cookie(String valor, Duration vida) {
        return ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(segura)
                .sameSite("Lax")
                .path("/")
                .maxAge(vida)
                .build();
    }

    private static String valor(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    record Guardado(String proveedor, String sujeto, String correo, boolean correoVerificado, String nombre,
                    long emitido) {
    }
}
