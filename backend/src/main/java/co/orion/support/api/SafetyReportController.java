package co.orion.support.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.shared.security.OrionUserDetails;
import co.orion.support.application.SafetyReportService;
import co.orion.support.application.SupportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El botón rojo del adulto en la clase de su adolescente (Bloque 12). */
@RestController
public class SafetyReportController {

    private final SafetyReportService reportes;

    public SafetyReportController(SafetyReportService reportes) {
        this.reportes = reportes;
    }

    @PostMapping("/api/v1/bookings/{id}/safety-report")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> reportar(@AuthenticationPrincipal OrionUserDetails principal,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody Cuerpo body) {
        SupportService.Hilo hilo = reportes.reportar(principal.user(), id, body.body());
        return Map.of("code", hilo.ticket().getCode());
    }

    public record Cuerpo(@NotBlank(message = "Cuéntanos qué pasó.") @Size(max = 4000) String body) {
    }
}
