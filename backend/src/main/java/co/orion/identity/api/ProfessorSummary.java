package co.orion.identity.api;

import java.util.UUID;

import co.orion.identity.domain.ProfessorProfile;

public record ProfessorSummary(UUID id, String fullName, String headline, String photoUrl) {

    public static ProfessorSummary from(ProfessorProfile profile) {
        return new ProfessorSummary(
                profile.getUserId(),
                profile.getUser().getFullName(),
                profile.getHeadline(),
                profile.getPhotoUrl());
    }
}
