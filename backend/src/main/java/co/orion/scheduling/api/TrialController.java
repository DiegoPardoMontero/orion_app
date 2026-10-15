package co.orion.scheduling.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.BookingService;
import co.orion.shared.security.OrionUserDetails;

/**
 * La clase de prueba con un profesor, vista por el estudiante: si la ofrece —siempre gratis, V65— y
 * si le toca. La pantalla solo ofrece reservarla cuando {@code available} es verdadero, y el motivo
 * dice por qué no cuando no.
 */
@RestController
public class TrialController {

    public record TrialResponse(boolean offered, boolean available, String reason) {
    }

    private final BookingService bookings;

    public TrialController(BookingService bookings) {
        this.bookings = bookings;
    }

    /**
     * Con {@code adolescentProfileId}, la prueba de ese hijo: es una por cada hijo (Pardo, 14/10/2026),
     * así que la de la madre y la de Sofi se miran por separado. Si el perfil no es del actor, la
     * reserva lo rechaza después; aquí solo se consulta.
     */
    @GetMapping("/api/v1/professors/{id}/trial")
    public TrialResponse trial(@PathVariable UUID id,
                               @RequestParam(required = false) UUID adolescentProfileId,
                               @AuthenticationPrincipal OrionUserDetails principal) {
        BookingService.Prueba p = bookings.pruebaCon(principal.user().getId(), id, adolescentProfileId);
        return new TrialResponse(p.ofrecida(), p.disponible(), p.motivo());
    }
}
