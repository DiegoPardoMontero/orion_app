package co.orion.identity.api;

import java.util.UUID;

import co.orion.identity.domain.User;

public record AdminUserResponse(UUID id,
                                String email,
                                String fullName,
                                String whatsappPhone,
                                String role,
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
                user.getStatus().name(),
                user.isEmailVerified(),
                founder,
                minors);
    }
}
