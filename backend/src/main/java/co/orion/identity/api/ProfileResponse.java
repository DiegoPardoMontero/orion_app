package co.orion.identity.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import co.orion.identity.domain.ProfessorProfile;

/**
 * @JsonProperty en isPublished no es decorativo: sin él Jackson serializa el componente como
 * "published" (le quita el prefijo "is" al accesor) mientras que al leerlo espera "isPublished",
 * de modo que la petición y la respuesta usarían nombres distintos para el mismo campo.
 */
public record ProfileResponse(UUID id,
                              String fullName,
                              String headline,
                              String bio,
                              String photoUrl,
                              @JsonProperty("isPublished") boolean isPublished) {

    public static ProfileResponse from(ProfessorProfile profile) {
        return new ProfileResponse(
                profile.getUserId(),
                profile.getUser().getFullName(),
                profile.getHeadline(),
                profile.getBio(),
                profile.getUser().getPhotoUrl(),
                profile.isPublished());
    }
}
