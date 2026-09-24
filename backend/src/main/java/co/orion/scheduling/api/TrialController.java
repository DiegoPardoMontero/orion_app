package co.orion.scheduling.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.BookingService;
import co.orion.shared.security.OrionUserDetails;

/**
 * La clase de prueba con un profesor, vista por el estudiante (Q7): si la ofrece, cuánto cuesta y
 * si le toca. La pantalla solo ofrece reservarla cuando {@code available} es verdadero, y el motivo
 * dice por qué no cuando no.
 */
@RestController
public class TrialController {

    public record TrialResponse(boolean offered, Long priceCop, boolean available, String reason) {
    }

    private final BookingService bookings;

    public TrialController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping("/api/v1/professors/{id}/trial")
    public TrialResponse trial(@PathVariable UUID id, @AuthenticationPrincipal OrionUserDetails principal) {
        BookingService.Prueba p = bookings.pruebaCon(principal.user().getId(), id);
        return new TrialResponse(p.ofrecida(), p.precioCop(), p.disponible(), p.motivo());
    }
}
