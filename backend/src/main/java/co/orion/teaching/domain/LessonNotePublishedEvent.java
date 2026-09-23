package co.orion.teaching.domain;

import java.util.UUID;

/**
 * Un acta publicada, o editada después de publicarse (D2: el estudiante se entera de que se
 * actualizó). Lo escuchan {@code messaging} —la notificación in-app, sin correo— y, cuando exista,
 * la práctica de la Parte B.
 */
public record LessonNotePublishedEvent(UUID noteId, UUID bookingId, UUID studentId, UUID professorId,
                                       boolean actualizacion) {
}
