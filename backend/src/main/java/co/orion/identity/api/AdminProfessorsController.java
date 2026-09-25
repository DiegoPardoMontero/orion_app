package co.orion.identity.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.FounderService;
import co.orion.identity.application.ProfessorInviteService;
import co.orion.identity.application.ProfessorProfileService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/** Invitación de profesores y tarifa. Solo ADMIN (la ruta /admin/** ya lo exige). */
@RestController
@RequestMapping("/api/v1/admin/professors")
public class AdminProfessorsController {

    private final ProfessorInviteService inviteService;
    private final ProfessorProfileService profileService;
    private final FounderService founders;

    public AdminProfessorsController(ProfessorInviteService inviteService,
                                     ProfessorProfileService profileService,
                                     FounderService founders) {
        this.inviteService = inviteService;
        this.profileService = profileService;
        this.founders = founders;
    }

    /**
     * Otorga el beneficio de profe fundador con la comisión y los meses vigentes (paso 2 del brief).
     * Para los profes que entraron antes de las invitaciones de fundador.
     */
    @PostMapping("/{professorId}/founder")
    public FounderView grantFounder(@AuthenticationPrincipal OrionUserDetails principal,
                                    @PathVariable UUID professorId) {
        return founders.grant(professorId, principal.user().getId());
    }

    /** Quita el beneficio: solo cambia las reservas nuevas. */
    @DeleteMapping("/{professorId}/founder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeFounder(@AuthenticationPrincipal OrionUserDetails principal,
                              @PathVariable UUID professorId) {
        founders.revoke(professorId, principal.user().getId());
    }

    /** Invita (o reenvía la invitación a) un profe por correo. Correo con cuenta → 409. */
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invite(@AuthenticationPrincipal OrionUserDetails principal,
                       @Valid @RequestBody InviteProfessorRequest body) {
        inviteService.invite(principal.user().getId(), body.email(), body.professorName(),
                body.founder() == null || body.founder(), body.inviterTitle());
    }

    /** Con qué nombre y cargo firma el admin sus invitaciones, para prellenar el formulario. */
    @GetMapping("/invite/defaults")
    public InviteDefaults inviteDefaults(@AuthenticationPrincipal OrionUserDetails principal) {
        return new InviteDefaults(principal.user().getFullName(), principal.user().getJobTitle());
    }

    public record InviteDefaults(String inviterName, String inviterTitle) {
    }

    /**
     * Fija la tarifa de un profesor, incluido el 0 de una clase gratuita: es la única puerta por la
     * que entra ese valor. Sirve para probar el flujo completo de reserva sin mover dinero.
     */
    @PutMapping("/{professorId}/rate")
    public RateBreakdownResponse setRate(@PathVariable UUID professorId,
                                         @Valid @RequestBody AdminRateRequest body) {
        return profileService.setRate(professorId, body.hourlyRateCop());
    }
}
