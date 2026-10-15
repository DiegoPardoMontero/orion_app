package co.orion.identity.api;

import java.util.UUID;

import co.orion.identity.domain.SignupIntent;
import co.orion.identity.domain.User;
import co.orion.identity.domain.UserRole;

public record AdminUserResponse(UUID id,
                                String email,
                                String fullName,
                                String whatsappPhone,
                                String role,
                                /**
                                 * Un estudiante que entró por «Quiero enseñar» y aún no está aprobado. El rol de la base
                                 * sigue siendo STUDENT; la pantalla del admin lo llama «Aspirante a profesor» (Pardo,
                                 * 15/10/2026: verlo como estudiante «me confundiría enormemente»).
                                 */
                                boolean teacherApplicant,
                                String status,
                                /** Si alguien confirmó ese correo. Sin confirmar, el admin lo puede corregir. */
                                boolean emailVerified,
                                /** El beneficio de profe fundador, o {@code null} si no lo tiene. */
                                FounderView founder,
                                /** La habilitación para adolescentes, solo en profes (Bloque 12). */
                                MinorsClearanceView minors) {

    public static AdminUserResponse from(User user) {
        return from(user, null);
    }

    public static AdminUserResponse from(User user, FounderView founder) {
        return from(user, founder, null);
    }

    public static AdminUserResponse from(User user, FounderView founder, MinorsClearanceView minors) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getWhatsappPhone(),
                user.getRole().name(),
                user.getRole() == UserRole.STUDENT && user.getSignupIntent() == SignupIntent.TEACH,
                user.getStatus().name(),
                user.isEmailVerified(),
                founder,
                minors);
    }
}
