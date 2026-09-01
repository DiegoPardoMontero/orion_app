package co.orion.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.ProfessorInviteService;
import jakarta.validation.Valid;

/** Invitación de profesores. Solo ADMIN (la ruta /admin/** ya lo exige). */
@RestController
@RequestMapping("/api/v1/admin/professors")
public class AdminProfessorsController {

    private final ProfessorInviteService inviteService;

    public AdminProfessorsController(ProfessorInviteService inviteService) {
        this.inviteService = inviteService;
    }

    /** Invita (o reenvía la invitación a) un profesor por correo. Email ya en uso → 409. */
    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invite(@Valid @RequestBody InviteProfessorRequest body) {
        inviteService.invite(body.email());
    }
}
