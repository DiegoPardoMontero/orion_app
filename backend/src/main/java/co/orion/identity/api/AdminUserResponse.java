package co.orion.identity.api;

import java.util.UUID;

import co.orion.identity.domain.User;

public record AdminUserResponse(UUID id,
                                String email,
                                String fullName,
                                String whatsappPhone,
                                String role,
                                String status,
                                /** El beneficio de profe fundador, o {@code null} si no lo tiene. */
                                FounderView founder) {

    public static AdminUserResponse from(User user) {
        return from(user, null);
    }

    public static AdminUserResponse from(User user, FounderView founder) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getWhatsappPhone(),
                user.getRole().name(),
                user.getStatus().name(),
                founder);
    }
}
