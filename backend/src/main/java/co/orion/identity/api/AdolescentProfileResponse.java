package co.orion.identity.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.identity.application.AdolescentProfileService;

/** Un adolescente a cargo del adulto que pregunta. Nunca sale a otra persona que no sea él o el profe de su clase. */
public record AdolescentProfileResponse(UUID id,
                                        String firstName,
                                        int birthYear,
                                        int age,
                                        String selfDeclaredLevel,
                                        String motivation,
                                        String authorizationVersion,
                                        Instant authorizedAt) {

    public static AdolescentProfileResponse from(AdolescentProfileService.Vista v) {
        return new AdolescentProfileResponse(
                v.profile().getId(),
                v.profile().getFirstName(),
                v.profile().getBirthYear(),
                v.age(),
                v.profile().getSelfDeclaredLevel() == null ? null : v.profile().getSelfDeclaredLevel().name(),
                v.profile().getMotivation(),
                v.authorization().map(a -> a.getDocumentVersion()).orElse(null),
                v.authorization().map(a -> a.getAcceptedAt()).orElse(null));
    }
}
