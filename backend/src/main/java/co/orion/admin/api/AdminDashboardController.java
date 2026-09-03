package co.orion.admin.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.admin.application.DashboardService;
import co.orion.admin.application.PurgeService;
import co.orion.shared.error.BusinessRuleViolationException;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;

/**
 * El panel del admin: el pulso del sistema y las herramientas para limpiarlo.
 *
 * El borrado va por DELETE con cuerpo de confirmación en vez de por un simple DELETE sin más: es
 * irreversible y destruye datos de varios módulos a la vez, así que exige haber visto antes la vista
 * previa y escribir la palabra de confirmación.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminDashboardController {

    private final DashboardService dashboard;
    private final PurgeService purge;

    public AdminDashboardController(DashboardService dashboard, PurgeService purge) {
        this.dashboard = dashboard;
        this.purge = purge;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return dashboard.build();
    }

    @GetMapping("/bookings/{id}/purge-preview")
    public PurgePreview previewBooking(@PathVariable UUID id) {
        return purge.previewBooking(id);
    }

    @DeleteMapping("/bookings/{id}")
    public PurgePreview purgeBooking(@AuthenticationPrincipal OrionUserDetails principal,
                                     @PathVariable UUID id,
                                     @Valid @RequestBody PurgeRequest body) {
        requireConfirmation(body);
        return purge.purgeBooking(id, principal.user(), body.reason());
    }

    @GetMapping("/users/{id}/purge-preview")
    public PurgePreview previewUser(@PathVariable UUID id) {
        return purge.previewUser(id);
    }

    @DeleteMapping("/users/{id}/purge")
    public PurgePreview purgeUser(@AuthenticationPrincipal OrionUserDetails principal,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody PurgeRequest body) {
        requireConfirmation(body);
        return purge.purgeUser(id, principal.user(), body.reason());
    }

    /** Escribir la palabra es la diferencia entre un clic accidental y una decisión. */
    private void requireConfirmation(PurgeRequest body) {
        if (!PurgeService.CONFIRMATION.equalsIgnoreCase(body.confirm().trim())) {
            throw new BusinessRuleViolationException(
                    "Para borrar definitivamente escribe " + PurgeService.CONFIRMATION);
        }
    }
}
