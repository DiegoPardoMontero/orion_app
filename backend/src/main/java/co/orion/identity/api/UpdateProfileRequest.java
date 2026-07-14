package co.orion.identity.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 120, message = "headline no puede superar 120 caracteres")
        String headline,

        String bio,

        @Size(max = 500, message = "photoUrl no puede superar 500 caracteres")
        String photoUrl,

        @JsonProperty("isPublished") boolean isPublished) {
}
