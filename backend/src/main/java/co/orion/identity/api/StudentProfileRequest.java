package co.orion.identity.api;

import java.util.List;

import jakarta.validation.constraints.Size;

/** Lo que el estudiante declara. Todo opcional: una ficha a medias es válida. */
public record StudentProfileRequest(
        @Size(max = 20) String selfDeclaredLevel,
        @Size(max = 5) String primaryLanguage,
        @Size(max = 280) String motivation,
        List<String> goalCodes) {
}
