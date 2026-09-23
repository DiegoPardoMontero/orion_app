package co.orion.teaching.domain;

import java.util.UUID;

/** Una clase cerrada hace una hora sin acta: el único recordatorio que recibirá el profesor. */
public record LessonNoteNudgeEvent(UUID bookingId, UUID professorId, UUID studentId) {
}
