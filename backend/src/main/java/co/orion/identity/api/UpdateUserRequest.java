package co.orion.identity.api;

import jakarta.validation.constraints.Size;

/** Todo opcional: es un PATCH, solo se toca lo que viene. */
public record UpdateUserRequest(
        @Size(max = 150) String fullName,
        @Size(max = 20) String whatsappPhone,
        String status) {
}
