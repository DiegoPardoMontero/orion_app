package co.orion.scheduling.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.ClassroomService;
import co.orion.shared.security.OrionUserDetails;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * La puerta del aula. Se pide al entrar y no se cachea: el token que devuelve caduca con la clase.
 */
@RestController
@RequestMapping("/api/v1/bookings/{id}/classroom")
public class ClassroomController {

    private final ClassroomService classroom;

    public ClassroomController(ClassroomService classroom) {
        this.classroom = classroom;
    }

    @GetMapping
    public ClassroomResponse enter(@PathVariable UUID id,
                                   @AuthenticationPrincipal OrionUserDetails principal) {
        return classroom.enter(id, principal.user());
    }

    /** «Estoy presente o cerca»: el adulto, antes de abrir la sala de su adolescente (Bloque 12). */
    @PostMapping("/guardian-present")
    public ClassroomResponse guardianPresent(@PathVariable UUID id,
                                             @AuthenticationPrincipal OrionUserDetails principal) {
        classroom.confirmGuardianPresent(id, principal.user());
        return classroom.enter(id, principal.user());
    }
}
