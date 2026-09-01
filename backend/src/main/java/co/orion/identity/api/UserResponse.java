package co.orion.identity.api;

import java.util.UUID;

import co.orion.identity.domain.User;
import co.orion.shared.security.OrionUserDetails;

public record UserResponse(UUID id, String email, String fullName, String role, String photoUrl) {

    public static UserResponse from(OrionUserDetails principal) {
        return from(principal.user());
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getPhotoUrl());
    }
}
