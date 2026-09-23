package co.orion.teaching.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Un acta publicada, o editada después de publicarse (D2: el estudiante se entera de que se
 * actualizó). Lo escuchan {@code messaging} —la notificación in-app, sin correo— y {@code practice},
 * que genera sus ejercicios con lo que el acta dice. Por eso lleva el contenido: la práctica no
 * importa nada de {@code teaching} salvo sus eventos, y el acta se puede corregir después.
 */
public record LessonNotePublishedEvent(UUID noteId, UUID bookingId, UUID studentId, UUID professorId,
                                       boolean actualizacion, String languageCode, Instant claseEmpieza,
                                       String workedOn, String recurringIssues, String nextSteps,
                                       List<Termino> vocabulario) {

    public record Termino(String term, String meaning) {
    }
}
