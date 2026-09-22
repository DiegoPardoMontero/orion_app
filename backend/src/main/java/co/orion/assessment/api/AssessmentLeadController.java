package co.orion.assessment.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.assessment.application.AssessmentLeadService;
import co.orion.shared.error.ConflictException;
import co.orion.shared.error.ResourceNotFoundException;
import co.orion.shared.security.IntentosDeAcceso;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La puerta del diagnóstico sin cuenta: un nombre y dos casillas, y a hablar.
 *
 * <p>Las dos casillas van separadas y las dos se exigen: la mayoría de edad (Orión es 18+, art. 7
 * de la Ley 1581) y la autorización de voz (art. 9 del Decreto 1377). Juntarlas en una sola
 * viciaría la autorización, igual que juntarla con los términos en el alta.
 *
 * <p>Es una API de pago abierta al público, así que tiene freno por IP además del tope diario de
 * gasto que ya apaga el diagnóstico entero.
 */
@RestController
@RequestMapping("/api/v1/assessment-leads")
public class AssessmentLeadController {

    private final AssessmentLeadService leads;
    private final LeadCookie cookie;
    private final IntentosDeAcceso intentos;

    public AssessmentLeadController(AssessmentLeadService leads, LeadCookie cookie,
                                    IntentosDeAcceso intentos) {
        this.leads = leads;
        this.cookie = cookie;
        this.intentos = intentos;
    }

    @PostMapping
    public ResponseEntity<LeadResponse> crear(@AuthenticationPrincipal OrionUserDetails principal,
                                              @Valid @RequestBody CrearLeadRequest body,
                                              HttpServletRequest http) {
        if (principal != null) {
            // Con cuenta, el diagnóstico va a la cuenta. Un lead aquí quedaría huérfano.
            throw new ConflictException("Ya tienes cuenta: el diagnóstico se guarda en ella.");
        }
        intentos.antesDeDiagnosticoAnonimo(http);

        AssessmentLeadService.Creado creado = leads.crear(body.firstName(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.con(creado.llave()).toString())
                .body(new LeadResponse(creado.lead().getFirstName()));
    }

    /** Quién es el lead de este dispositivo, si hay uno sin reclamar: para no volver a preguntar. */
    @GetMapping("/me")
    public LeadResponse yo(HttpServletRequest http) {
        return leads.porLlave(LeadClaimFilter.llaveDe(http))
                .map(l -> new LeadResponse(l.getFirstName()))
                .orElseThrow(() -> new ResourceNotFoundException("No hay diagnóstico sin cuenta aquí."));
    }

    public record CrearLeadRequest(
            @NotBlank(message = "Dinos cómo te llamas.") @Size(max = 60) String firstName,
            @AssertTrue(message = "Orión está disponible solo para mayores de 18 años.") boolean adult,
            @AssertTrue(message = "Necesitamos tu autorización para procesar tu voz.") boolean acceptsVoice) {
    }

    public record LeadResponse(String firstName) {
    }
}
