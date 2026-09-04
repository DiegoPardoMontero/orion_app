package co.orion.identity.api;

import java.util.UUID;

import co.orion.identity.domain.User;
import co.orion.shared.security.OrionUserDetails;

/**
 * Quién es quien está dentro.
 *
 * <p>{@code role} es el <strong>rol efectivo</strong>, no la columna: quien se registró para
 * enseñar y espera una decisión llega como {@code TEACHER_APPLICANT}, que es también la autoridad
 * con la que el backend lo autoriza. Devolver aquí {@code STUDENT} y autorizar como aspirante haría
 * que el frontend dibujara un menú de estudiante contra una API que responde 403 a todo — dos
 * versiones de la verdad, y la peor de las dos es la que ve la persona.
 */
public record UserResponse(UUID id, String email, String fullName, String role, String photoUrl) {

    public static UserResponse from(OrionUserDetails principal) {
        User user = principal.user();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                principal.rolEfectivo(),
                user.getPhotoUrl());
    }
}
