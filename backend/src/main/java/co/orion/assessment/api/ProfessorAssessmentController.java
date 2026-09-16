package co.orion.assessment.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.assessment.application.AssessmentService;
import co.orion.assessment.domain.AssessmentStatus;
import co.orion.assessment.domain.ConfidenceAssessment;
import co.orion.assessment.persistence.ConfidenceAssessmentRepository;
import co.orion.scheduling.persistence.BookingRepository;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.security.OrionUserDetails;

/**
 * El diagnóstico de un estudiante, visto por su profesor.
 *
 * <p>Dos límites, y los dos importan. Solo lo ve quien tiene una reserva con esa persona —en
 * cualquier otro caso, 404, porque el diagnóstico de un desconocido no existe para ti— y
 * <strong>nunca</strong> incluye la transcripción. Saber que alguien se traba al empezar es útil
 * para preparar la clase; leer lo que dijo mientras practicaba no lo es, y convierte una
 * herramienta en vigilancia.
 */
@RestController
@RequestMapping("/api/v1/professors/me/students")
public class ProfessorAssessmentController {

    private final ConfidenceAssessmentRepository assessments;
    private final AssessmentService service;
    private final BookingRepository bookings;

    public ProfessorAssessmentController(ConfidenceAssessmentRepository assessments,
                                         AssessmentService service,
                                         BookingRepository bookings) {
        this.assessments = assessments;
        this.service = service;
        this.bookings = bookings;
    }

    @GetMapping("/{studentId}/assessment")
    public AssessmentResponse ofStudent(@AuthenticationPrincipal OrionUserDetails principal,
                                        @PathVariable UUID studentId) {
        UUID profesor = principal.user().getId();
        if (!bookings.existsByProfessorIdAndStudentId(profesor, studentId)) {
            throw new ResourceNotFoundException("Diagnóstico no encontrado");
        }

        ConfidenceAssessment ultima = assessments.findByUserIdOrderByStartedAtDesc(studentId).stream()
                .filter(a -> a.getStatus() == AssessmentStatus.COMPLETED)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Diagnóstico no encontrado"));

        // La misma vista del dueño, que por construcción no lleva turnos. Un DTO aparte para el
        // profesor sería un sitio más del que la transcripción podría escaparse.
        return AssessmentViews.of(ultima, List.of());
    }
}
