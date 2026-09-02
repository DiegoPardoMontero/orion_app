package co.orion.identity.api;

import java.time.Instant;
import java.util.UUID;

import co.orion.identity.domain.TeacherApplicationEvent;

/** Una entrada del historial de la postulación. */
public record ApplicationEventView(
        String eventType,
        UUID actorId,
        String note,
        Instant createdAt) {

    public static ApplicationEventView of(TeacherApplicationEvent e) {
        return new ApplicationEventView(e.getEventType().name(), e.getActorId(), e.getNote(), e.getCreatedAt());
    }
}
