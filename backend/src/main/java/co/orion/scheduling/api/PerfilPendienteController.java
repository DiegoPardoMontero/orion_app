package co.orion.scheduling.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.PerfilDelProfesor;
import co.orion.shared.security.OrionUserDetails;

/** Lo que le falta al profesor para recibir estudiantes: la franja de Rigel lo lee de aquí. */
@RestController
public class PerfilPendienteController {

    public record PendingResponse(List<String> missing) {
    }

    private final PerfilDelProfesor perfil;

    public PerfilPendienteController(PerfilDelProfesor perfil) {
        this.perfil = perfil;
    }

    @GetMapping("/api/v1/me/profile/pending")
    public PendingResponse pendiente(@AuthenticationPrincipal OrionUserDetails principal) {
        return new PendingResponse(perfil.faltan(principal.user().getId()));
    }
}
