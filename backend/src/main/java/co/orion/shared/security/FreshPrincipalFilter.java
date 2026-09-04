package co.orion.shared.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import co.orion.identity.domain.User;
import co.orion.identity.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Vuelve a leer al usuario de la base en cada petición autenticada.
 *
 * <p>La sesión guarda el principal tal como estaba al entrar, y eso convierte cualquier cambio de
 * cuenta en un cambio que no ocurre hasta que la persona vuelve a entrar: al aspirante que acaban
 * de aprobar le seguirían faltando los permisos de profesor, y —peor— a quien acaban de desactivar
 * le seguirían sobrando todos los suyos hasta que cerrara sesión.
 *
 * <p>Es una consulta por petición. A la escala de Orión eso no se nota, y compra que el estado de
 * la cuenta sea siempre el de la base y no el de un recuerdo.
 */
@Component
public class FreshPrincipalFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public FreshPrincipalFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        if (actual != null && actual.getPrincipal() instanceof OrionUserDetails principal) {
            User fresco = users.findById(principal.user().getId()).orElse(null);
            if (fresco == null || !fresco.isActive()) {
                // La cuenta ya no existe o quedó inactiva: la sesión deja de valer aquí mismo.
                SecurityContextHolder.clearContext();
                request.getSession(false);
            } else if (cambió(principal.user(), fresco)) {
                OrionUserDetails renovado = new OrionUserDetails(fresco);
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                renovado, actual.getCredentials(), renovado.getAuthorities()));
            }
        }
        chain.doFilter(request, response);
    }

    /** Lo que hace distinto a un principal: qué es la cuenta y a qué vino. */
    private boolean cambió(User enSesion, User enBase) {
        return enSesion.getRole() != enBase.getRole()
                || enSesion.getSignupIntent() != enBase.getSignupIntent();
    }
}
