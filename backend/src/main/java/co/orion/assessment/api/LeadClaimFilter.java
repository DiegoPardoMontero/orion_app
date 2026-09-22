package co.orion.assessment.api;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import co.orion.assessment.application.AssessmentLeadService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cuando alguien que hizo el diagnóstico sin cuenta entra con una, sus diagnósticos pasan a ella.
 *
 * <p><strong>Por qué un filtro y no una llamada desde el login.</strong> Se entra por tres puertas
 * —crear cuenta, iniciar sesión y, pronto, Google, Apple o Facebook— y las tres son de
 * {@code identity}, que no puede depender de {@code assessment} (es al revés). El filtro mira
 * cualquier petición de un estudiante autenticado que todavía traiga la cookie del lead, reclama y
 * borra la cookie. La primera petición después de entrar —el {@code /auth/me} que la aplicación
 * hace siempre— es la que lo hace.
 *
 * <p>Sin cookie no hay consulta a la base: el costo para todas las demás peticiones es mirar una
 * cabecera. Registrado como filtro de servlet, corre después de la cadena de seguridad, cuando ya
 * se sabe quién es.
 */
@Component
public class LeadClaimFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LeadClaimFilter.class);

    private final AssessmentLeadService leads;
    private final LeadCookie cookie;

    public LeadClaimFilter(AssessmentLeadService leads, LeadCookie cookie) {
        this.leads = leads;
        this.cookie = cookie;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String llave = llaveDe(request);
        if (llave != null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof OrionUserDetails principal
                    && "STUDENT".equals(principal.rolEfectivo())) {
                try {
                    leads.reclamar(llave, principal.user());
                } catch (RuntimeException ex) {
                    // Que falle la mudanza no puede tumbar la petición de alguien que acaba de
                    // entrar. El lead sigue ahí; el job lo borra si nunca se reclama.
                    log.error("No se pudo reclamar el lead de la cuenta {}", principal.user().getId(), ex);
                }
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.borrar().toString());
            }
        }
        chain.doFilter(request, response);
    }

    static String llaveDe(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (AssessmentLeadService.COOKIE.equals(c.getName()) && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
