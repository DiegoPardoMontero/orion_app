package co.orion.assessment.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.assessment.application.AssessmentService;
import co.orion.assessment.application.VoiceSession;
import co.orion.assessment.domain.ConfidenceAssessment;
import co.orion.shared.security.OrionUserDetails;
import co.orion.shared.time.BusinessZone;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** El diagnóstico de confianza: empezar, alimentar, cerrar y leer. Todo del dueño y de nadie más. */
@RestController
@RequestMapping("/api/v1")
public class AssessmentController {

    private final AssessmentService assessments;

    public AssessmentController(AssessmentService assessments) {
        this.assessments = assessments;
    }

    /**
     * El consentimiento de voz, separado de los términos a propósito: la autorización para tratar
     * un dato biométrico tiene que ser previa, expresa y específica.
     */
    @PostMapping("/me/voice-consent")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse consent(@AuthenticationPrincipal OrionUserDetails principal,
                                   HttpServletRequest http) {
        var guardado = assessments.acceptConsent(principal.user(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        return new ConsentResponse(guardado.getVersion(),
                ZonedDateTime.ofInstant(guardado.getAcceptedAt(), BusinessZone.BOGOTA));
    }

    /** Revocar. Los turnos de esta persona se borran en la siguiente corrida del job. */
    @PostMapping("/me/voice-consent/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal OrionUserDetails principal) {
        assessments.revokeConsent(principal.user());
    }

    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public StartedResponse start(@AuthenticationPrincipal OrionUserDetails principal,
                                 @Valid @RequestBody(required = false) StartAssessmentRequest body) {
        String idioma = body == null || body.languageCode() == null || body.languageCode().isBlank()
                ? "EN" : body.languageCode().toUpperCase();

        AssessmentService.Iniciada iniciada = assessments.start(principal.user(), idioma);
        VoiceSession voz = iniciada.voice();

        return new StartedResponse(
                iniciada.assessment().getId(),
                idioma,
                voz.clientSecret(),
                voz.model(),
                ZonedDateTime.ofInstant(voz.expiresAt(), BusinessZone.BOGOTA));
    }

    @GetMapping("/assessments/{id}")
    public AssessmentResponse one(@AuthenticationPrincipal OrionUserDetails principal,
                                  @PathVariable UUID id) {
        ConfidenceAssessment mia = assessments.mia(principal.user(), id);
        return AssessmentViews.of(mia, assessments.recommendationsOf(id));
    }

    @PostMapping("/assessments/{id}/turns")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addTurn(@AuthenticationPrincipal OrionUserDetails principal,
                        @PathVariable UUID id,
                        @Valid @RequestBody AddTurnRequest body) {
        assessments.addTurn(principal.user(), id, body.turnIndex(), body.speaker(),
                body.transcript(), body.latencyMs(), body.durationMs());
    }

    @PostMapping("/assessments/{id}/complete")
    public AssessmentResponse complete(@AuthenticationPrincipal OrionUserDetails principal,
                                       @PathVariable UUID id,
                                       @RequestBody(required = false) StartAssessmentRequest body) {
        List<String> objetivos = body == null || body.goals() == null ? List.of() : body.goals();
        ConfidenceAssessment cerrada = assessments.complete(principal.user(), id, objetivos);
        return AssessmentViews.of(cerrada, assessments.recommendationsOf(id));
    }

    @PostMapping("/assessments/{id}/abandon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id) {
        assessments.abandon(principal.user(), id);
    }

    /** El historial: la base de la curva entre un diagnóstico y el siguiente. */
    @GetMapping("/me/assessments")
    public List<AssessmentResponse> mine(@AuthenticationPrincipal OrionUserDetails principal) {
        return assessments.history(principal.user()).stream()
                .map(a -> AssessmentViews.of(a, assessments.recommendationsOf(a.getId())))
                .toList();
    }

    public record ConsentResponse(String version, ZonedDateTime acceptedAt) {
    }

    /** Lo que necesita el navegador para conectarse. La llave de la cuenta nunca sale de aquí. */
    public record StartedResponse(UUID assessmentId, String languageCode, String clientSecret,
                                  String model, ZonedDateTime expiresAt) {
    }
}
