package co.orion.lifecycle.domain;

import java.util.UUID;

/** Toca recordar esta clase. Se publica una vez por clase y tipo: la tabla lo garantiza. */
public record ClassReminderDueEvent(UUID bookingId, ClassReminderKind kind) {
}
