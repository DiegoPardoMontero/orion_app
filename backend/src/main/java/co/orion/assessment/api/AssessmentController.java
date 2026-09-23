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
import org.springframework.web.server.ResponseStatusException;

import co.orion.assessment.application.AssessmentLeadService;
import co.orion.assessment.application.AssessmentService;
import co.orion.assessment.application.Evaluado;
import co.orion.assessment.application.VoiceSession;
import co.orion.assessment.domain.ConfidenceAssessment;
import co.orion.shared.error.ForbiddenException;
import co.orion.shared.security.OrionUserDetails;
import co.orion.shared.security.IntentosDeAcceso;
import co.orion.shared.time.BusinessZone;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El diagnóstico de confianza: empezar, alimentar, cerrar y leer. Todo del dueño y de nadie más.
 *
 * <p>El dueño es una cuenta de estudiante o, sin cuenta, el lead de este dispositivo (la cookie
 * {@code ORION_LEAD}). Por eso estas rutas son públicas en {@code SecurityConfig} y la puerta está
 * aquí: sin ninguno de los dos, 401; con una cuenta que no es de estudiante, 403.
 */
@RestController
@RequestMapping("/api/v1")
public class AssessmentController {

    private final AssessmentService assessments;
    private final AssessmentLeadService leads;
    private final IntentosDeAcceso intentos;

    public AssessmentController(AssessmentService assessments, AssessmentLeadService leads,
                                IntentosDeAcceso intentos) {
        this.assessments = assessments;
        this.leads = leads;
        this.intentos = intentos;
    }

    /** Quién llama. La cuenta manda sobre la cookie: con sesión, el diagnóstico es de la cuenta. */
    private Evaluado quien(OrionUserDetails principal, HttpServletRequest http) {
        if (principal != null) {
            if (!"STUDENT".equals(principal.rolEfectivo())) {
                throw new ForbiddenException("El diagnóstico es para estudiantes.");
            }
            return Evaluado.cuenta(principal.user());
        }
        return leads.porLlave(LeadClaimFilter.llaveDe(http))
                .map(Evaluado::lead)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Dinos tu nombre antes de empezar."));
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
                                 @Valid @RequestBody(required = false) StartAssessmentRequest body,
                                 HttpServletRequest http) {
        String idioma = body == null || body.languageCode() == null || body.languageCode().isBlank()
                ? "EN" : body.languageCode().toUpperCase();

        Evaluado quien = quien(principal, http);
        intentos.antesDeAbrirVoz(quien.esLead() ? quien.leadId() : quien.userId());
        AssessmentService.Iniciada iniciada = assessments.start(quien, idioma);
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
                                  @PathVariable UUID id, HttpServletRequest http) {
        ConfidenceAssessment mia = assessments.mia(quien(principal, http), id);
        return AssessmentViews.of(mia, assessments.recommendationsOf(id));
    }

    @PostMapping("/assessments/{id}/turns")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addTurn(@AuthenticationPrincipal OrionUserDetails principal,
                        @PathVariable UUID id,
                        @Valid @RequestBody AddTurnRequest body,
                        HttpServletRequest http) {
        assessments.addTurn(quien(principal, http), id, body.turnIndex(), body.speaker(),
                body.transcript(), body.latencyMs(), body.durationMs());
    }

    /**
     * La traducción al español de una frase de Meissa, mientras la dice. {@code translation} nulo
     * cuando no hay nada que mostrar debajo: sin presupuesto, el proveedor no llegó a tiempo o la
     * frase ya era español.
     */
    @PostMapping("/assessments/{id}/translate")
    public TraduccionResponse translate(@AuthenticationPrincipal OrionUserDetails principal,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody TraducirRequest body,
                                        HttpServletRequest http) {
        Evaluado quien = quien(principal, http);
        intentos.antesDeTraducir(id);
        return new TraduccionResponse(assessments.traducir(quien, id, body.text().strip()).orElse(null));
    }

    public record TraducirRequest(@NotBlank @Size(max = 400) String text) {
    }

    public record TraduccionResponse(String translation) {
    }

    @PostMapping("/assessments/{id}/complete")
    public AssessmentResponse complete(@AuthenticationPrincipal OrionUserDetails principal,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody(required = false) StartAssessmentRequest body,
                                       HttpServletRequest http) {
        List<String> objetivos = body == null || body.goals() == null ? List.of() : body.goals();
        ConfidenceAssessment cerrada = assessments.complete(quien(principal, http), id, objetivos);
        return AssessmentViews.of(cerrada, assessments.recommendationsOf(id));
    }

    @PostMapping("/assessments/{id}/abandon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@AuthenticationPrincipal OrionUserDetails principal, @PathVariable UUID id,
                        HttpServletRequest http) {
        assessments.abandon(quien(principal, http), id);
    }

    /** El historial: la base de la curva entre un diagnóstico y el siguiente. */
    @GetMapping("/me/assessments")
    public List<AssessmentResponse> mine(@AuthenticationPrincipal OrionUserDetails principal,
                                         HttpServletRequest http) {
        return assessments.history(quien(principal, http)).stream()
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
