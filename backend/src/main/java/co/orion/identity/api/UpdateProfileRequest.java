package co.orion.identity.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 120, message = "headline no puede superar 120 caracteres")
        String headline,

        String bio,

        // La foto ya no se edita por URL: se sube por POST /api/v1/me/photo.
        @JsonProperty("isPublished") boolean isPublished) {
}
