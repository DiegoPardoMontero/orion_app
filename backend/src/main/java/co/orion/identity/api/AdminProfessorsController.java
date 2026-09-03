package co.orion.identity.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.ProfessorInviteService;
import co.orion.identity.application.ProfessorProfileService;
import jakarta.validation.Valid;

/** Invitación de profesores y tarifa. Solo ADMIN (la ruta /admin/** ya lo exige). */
@RestController
@RequestMapping("/api/v1/admin/professors")
public class AdminProfessorsController {

    private final ProfessorInviteService inviteService;
    private final ProfessorProfileService profileService;

    public AdminProfessorsController(ProfessorInviteService inviteService,
                                     ProfessorProfileService profileService) {
        this.inviteService = inviteService;
        this.profileService = profileService;
    }

    /** Invita (o reenvía la invitación a) un profesor por correo. Email ya en uso → 409. */
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invite(@Valid @RequestBody InviteProfessorRequest body) {
        inviteService.invite(body.email());
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
