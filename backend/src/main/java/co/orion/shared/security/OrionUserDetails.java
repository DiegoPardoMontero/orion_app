package co.orion.shared.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import co.orion.identity.domain.SignupIntent;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;

/** Envuelve al User del dominio para que Spring Security lo entienda, sin contaminar la entidad. */
public class OrionUserDetails implements UserDetails {

    private final User user;

    public OrionUserDetails(User user) {
        this.user = user;
    }

    public User user() {
        return user;
    }

    /**
     * Quien se registró para enseñar y todavía espera una decisión <strong>no</strong> es un
     * estudiante, y por eso no lleva {@code ROLE_STUDENT}.
     *
     * <p>Aquí está la palanca entera: toda la experiencia del estudiante —reservar, pagar, el
     * saldo, la ficha, la gamificación— cuelga de esa autoridad en {@code SecurityConfig}, así que
     * no dársela cierra las decenas de puertas de una vez, en lugar de repartir la misma condición
     * por cada servicio y confiar en no olvidarse de ninguno.
     *
     * <p>Cuando su postulación se aprueba, el rol pasa a {@code PROFESSOR} y esta condición deja de
     * aplicar sola. Si se rechaza, la intención vuelve a {@code LEARN} y la cuenta sigue siendo una
     * cuenta de estudiante normal.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // hasRole("ADMIN") busca la authority "ROLE_ADMIN": el prefijo es obligatorio.
        return List.of(new SimpleGrantedAuthority("ROLE_" + rolEfectivo()));
    }

    /** El rol tal como lo ve la autorización: el de la cuenta, salvo si aún es un aspirante. */
    public String rolEfectivo() {
        return esAspiranteAProfesor() ? "TEACHER_APPLICANT" : user.getRole().name();
    }

    public boolean esAspiranteAProfesor() {
        return user.getRole() == UserRole.STUDENT
                && user.getSignupIntent() == SignupIntent.TEACH;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
