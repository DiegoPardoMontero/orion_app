package co.orion.scheduling.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.persistence.BookingRepository;
import co.orion.scheduling.persistence.RoomParticipations;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.security.OrionUserDetails;
import co.orion.shared.time.BusinessZone;

/**
 * Lo que el aula cuenta, para quien lo necesita (Pardo, 22/09/2026): el profesor ve cuánto habló su
 * estudiante en sus últimas clases juntos, para preparar la siguiente; el admin ve, por profesor,
 * qué parte de la palabra tuvieron sus estudiantes y su puntualidad. El estudiante no ve esto.
 *
 * <p>La puntualidad es solo informativa: no alimenta strikes ni sanciones.
 */
@RestController
public class ClassroomStatsController {

    private static final int ULTIMAS = 5;
    private static final Duration VENTANA_ADMIN = Duration.ofDays(90);

    private final RoomParticipations participaciones;
    private final BookingRepository bookings;
    private final Clock clock;

    public ClassroomStatsController(RoomParticipations participaciones, BookingRepository bookings,
                                    Clock clock) {
        this.participaciones = participaciones;
        this.bookings = bookings;
        this.clock = clock;
    }

    /**
     * 404 y no 403 si el profesor nunca tuvo clase con ese estudiante: no se confirma que exista
     * alguien con quien no hay relación, igual que la ficha y el diagnóstico.
     */
    @GetMapping("/api/v1/professors/me/students/{studentId}/classroom")
    public List<ClaseView> deMiEstudiante(@AuthenticationPrincipal OrionUserDetails principal,
                                          @PathVariable UUID studentId) {
        UUID yo = principal.user().getId();
        if (!bookings.existsByProfessorIdAndStudentId(yo, studentId)) {
            throw new ResourceNotFoundException("Estudiante no encontrado");
        }
        return participaciones.ultimasClases(yo, studentId, clock.instant(), ULTIMAS).stream()
                .map(c -> new ClaseView(ZonedDateTime.ofInstant(c.empezo(), BusinessZone.BOGOTA),
                        c.estudianteMs(), c.profesorMs()))
                .toList();
    }

    @GetMapping("/api/v1/admin/classroom-stats")
    public List<RoomParticipations.AulaDelProfesor> porProfesor() {
        Instant ahora = clock.instant();
        return participaciones.porProfesor(ahora.minus(VENTANA_ADMIN), ahora);
    }

    public record ClaseView(ZonedDateTime startsAt, Integer studentSpeakingMs, Integer professorSpeakingMs) {
    }
}
