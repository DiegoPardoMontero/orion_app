package co.orion.identity.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.identity.application.MinorsClearanceService;
import co.orion.shared.security.OrionUserDetails;
import jakarta.servlet.http.HttpServletRequest;

/**
 * «Clases con adolescentes» en Mi perfil del profe (Bloque 12): cómo va su habilitación y aceptar el
 * código de conducta. Los dos documentos se suben por el mismo endpoint de siempre
 * ({@code /me/teacher-application/documents}) con sus tipos nuevos.
 */
@RestController
@RequestMapping("/api/v1/me/profile/minors")
public class MyMinorsClearanceController {

    private final MinorsClearanceService clearance;

    public MyMinorsClearanceController(MinorsClearanceService clearance) {
        this.clearance = clearance;
    }

    @GetMapping
    public MinorsClearanceView mine(@AuthenticationPrincipal OrionUserDetails principal) {
        return clearance.view(principal.user().getId());
    }

    @PostMapping("/conduct")
    public MinorsClearanceView acceptConduct(@AuthenticationPrincipal OrionUserDetails principal,
                                             HttpServletRequest http) {
        return clearance.acceptConduct(principal.user(), http.getRemoteAddr(), http.getHeader("User-Agent"));
    }
}
