package co.orion.scheduling.api;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import co.orion.scheduling.application.StudentProgressService;
import co.orion.scheduling.domain.BusinessZone;

/**
 * El panel del estudiante. Los instantes salen en hora de Bogotá, como en el resto de la API.
 *
 * <p>{@code lessonsByDay} solo trae los días CON clase: un año son 365 fechas y casi todas serían
 * ceros que la interfaz ya sabe dibujar sola. {@code mapFrom} y {@code today} le dicen dónde empieza
 * y dónde termina la cuadrícula, para que no tenga que deducir el rango del propio contenido —si lo
 * dedujera, un estudiante sin clases se quedaría sin mapa que dibujar.
 */
public record MyProgressResponse(int lessonsTaken,
                                 long minutesTotal,
                                 int currentStreakWeeks,
                                 int bestStreakWeeks,
                                 NextLesson nextLesson,
                                 List<ProfessorTaught> professors,
                                 Map<LocalDate, Integer> lessonsByDay,
                                 LocalDate mapFrom,
                                 LocalDate today) {

    public record NextLesson(UUID id, ZonedDateTime startsAt, String modality, String meetingLink,
                             UUID professorId, String professorName, String professorPhotoUrl) {
    }

    public record ProfessorTaught(UUID id, String fullName, String photoUrl, String headline,
                                  int lessons, ZonedDateTime lastLessonAt) {
    }

    public static MyProgressResponse from(StudentProgressService.Progreso progreso) {
        return new MyProgressResponse(
                progreso.lessonsTaken(),
                progreso.minutesTotal(),
                progreso.currentStreakWeeks(),
                progreso.bestStreakWeeks(),
                progreso.nextLesson() == null ? null : new NextLesson(
                        progreso.nextLesson().id(),
                        progreso.nextLesson().startsAt().atZone(BusinessZone.BOGOTA),
                        progreso.nextLesson().modality(),
                        progreso.nextLesson().meetingLink(),
                        progreso.nextLesson().professorId(),
                        progreso.nextLesson().professorName(),
                        progreso.nextLesson().professorPhotoUrl()),
                progreso.professors().stream()
                        .map(p -> new ProfessorTaught(p.id(), p.fullName(), p.photoUrl(), p.headline(),
                                p.lessons(), p.lastLessonAt().atZone(BusinessZone.BOGOTA)))
                        .toList(),
                progreso.lessonsByDay(),
                progreso.mapFrom(),
                progreso.today());
    }
}
