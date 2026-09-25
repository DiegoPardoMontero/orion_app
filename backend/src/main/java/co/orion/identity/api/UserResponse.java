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
 *
 * <p>{@code adultConfirmed} y {@code emailVerified} son las dos condiciones que hacen falta para
 * reservar. El frontend las usa para avisar antes de que la persona llegue al botón; el backend no
 * se fía de eso y las vuelve a comprobar en {@code BookingService}, que es donde importan.
 *
 * <p>{@code hasWhatsapp} es falso en las cuentas que nacieron cuando el número era opcional, o que
 * creó el admin sin él: la app se lo pide al entrar.
 */
public record UserResponse(UUID id, String email, String fullName, String role, String photoUrl,
                           boolean adultConfirmed, boolean emailVerified, boolean hasPassword,
                           boolean hasWhatsapp) {

    public static UserResponse from(OrionUserDetails principal) {
        User user = principal.user();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                principal.rolEfectivo(),
                user.getPhotoUrl(),
                user.hasConfirmedAdulthood(),
                user.isEmailVerified(),
                // Sin contraseña propia (entró con Google): «Cambiar contraseña» se vuelve «Crear una».
                user.hasPasswordSet(),
                user.getWhatsappPhone() != null && !user.getWhatsappPhone().isBlank());
    }
}
