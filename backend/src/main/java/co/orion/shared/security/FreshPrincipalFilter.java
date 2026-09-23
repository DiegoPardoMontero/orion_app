package co.orion.shared.security;

import java.io.IOException;
import java.util.Objects;

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
import jakarta.servlet.http.HttpSession;

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
 *
 * <p>El principal se sustituye <strong>siempre</strong>, sin comparar campos. Antes solo se
 * sustituía si cambiaba el rol o la intención de alta, y esa lista se quedó corta en cuanto la
 * cuenta ganó estados nuevos: quien declaraba su mayoría de edad recibía su 204, la fila quedaba
 * escrita, y {@code /auth/me} seguía respondiendo con el recuerdo — el diálogo, que no tiene
 * salida, no se iba nunca. Mantener una lista de "qué campos cuentan" es apostar a acordarse de
 * ampliarla; la fila ya está leída, así que copiarla entera no cuesta nada más.
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
            if (fresco == null || !fresco.isActive() || cambioLaContrasena(principal.user(), fresco)) {
                // La cuenta ya no existe, quedó inactiva o cambió de contraseña: la sesión deja de
                // valer aquí mismo. Antes solo se vaciaba el contexto de esta petición y la sesión
                // seguía viva en el servidor; ahora se invalida.
                SecurityContextHolder.clearContext();
                HttpSession sesion = request.getSession(false);
                if (sesion != null) {
                    sesion.invalidate();
                }
            } else {
                OrionUserDetails renovado = new OrionUserDetails(fresco);
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                renovado, actual.getCredentials(), renovado.getAuthorities()));
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Cambiar la contraseña es, casi siempre, echar a alguien: quien sospecha que le robaron la
     * cuenta la cambia para que el otro deje de entrar. Si las sesiones abiertas sobrevivieran, el
     * intruso seguiría dentro con la llave vieja. La sesión recuerda el hash con el que entró; si ya
     * no es el de la base, se cierra. La de quien la cambió se renueva en el mismo cambio.
     */
    private static boolean cambioLaContrasena(User recordado, User fresco) {
        return !Objects.equals(recordado.getPasswordHash(), fresco.getPasswordHash());
    }
}
