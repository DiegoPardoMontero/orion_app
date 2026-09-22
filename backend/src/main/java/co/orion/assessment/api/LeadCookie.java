package co.orion.assessment.api;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import co.orion.assessment.application.AssessmentLeadService;

/**
 * La cookie del lead: httpOnly, SameSite=Lax y con {@code Secure} donde lo lleva la de sesión.
 *
 * <p>SameSite=Lax es lo que la protege de otro sitio que intente usarla a espaldas de la persona:
 * el navegador no la manda en peticiones cruzadas que no sean navegación. Vive lo mismo que el
 * lead antes de que el job lo borre; más no tendría a qué apuntar.
 */
@Component
public class LeadCookie {

    private static final Duration VIDA = Duration.ofDays(30);

    private final boolean segura;

    public LeadCookie(@Value("${server.servlet.session.cookie.secure:false}") boolean segura) {
        this.segura = segura;
    }

    public ResponseCookie con(String llave) {
        return base(llave).maxAge(VIDA).build();
    }

    public ResponseCookie borrar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(AssessmentLeadService.COOKIE, valor)
                .httpOnly(true)
                .secure(segura)
                .sameSite("Lax")
                .path("/api");
    }
}
