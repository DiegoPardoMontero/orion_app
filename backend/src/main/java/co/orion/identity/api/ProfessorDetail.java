package co.orion.identity.api;

import java.util.UUID;

import co.orion.identity.domain.ProfessorProfile;

public record ProfessorDetail(UUID id,
                              String fullName,
                              String headline,
                              String bio,
                              String photoUrl,
                              String whatsappPhone) {

    public static ProfessorDetail from(ProfessorProfile profile) {
        return new ProfessorDetail(
                profile.getUserId(),
                profile.getUser().getFullName(),
                profile.getHeadline(),
                profile.getBio(),
                profile.getUser().getPhotoUrl(),
                profile.getUser().getWhatsappPhone());
    }
}
