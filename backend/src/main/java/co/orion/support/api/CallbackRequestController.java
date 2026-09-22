package co.orion.support.api;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.shared.security.IntentosDeAcceso;
import co.orion.shared.security.OrionUserDetails;
import co.orion.shared.time.BusinessZone;
import co.orion.support.application.CallbackRequestService;
import co.orion.support.domain.CallbackRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * «¿Prefieres que te llame una persona?»: el pedido público y la bandeja del administrador.
 *
 * <p>El pedido no exige cuenta —es para quien todavía no se anima a hablar con Meissa— y por eso
 * tiene freno por IP. La bandeja vive bajo {@code /api/v1/admin/**}, que SecurityConfig reserva al
 * rol ADMIN.
 */
@RestController
public class CallbackRequestController {

    private final CallbackRequestService solicitudes;
    private final IntentosDeAcceso intentos;

    public CallbackRequestController(CallbackRequestService solicitudes, IntentosDeAcceso intentos) {
        this.solicitudes = solicitudes;
        this.intentos = intentos;
    }

    @PostMapping("/api/v1/callback-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido pedir(@Valid @RequestBody PedirLlamadaRequest body, HttpServletRequest http) {
        intentos.antesDeSolicitarLlamada(http);
        CallbackRequest guardada = solicitudes.pedir(body.firstName(), body.whatsapp(),
                http.getRemoteAddr());
        return new Pedido(guardada.getFirstName());
    }

    @GetMapping("/api/v1/admin/callback-requests")
    public List<SolicitudView> bandeja() {
        return solicitudes.bandeja().stream().map(SolicitudView::of).toList();
    }

    @PostMapping("/api/v1/admin/callback-requests/{id}/attend")
    public SolicitudView atender(@AuthenticationPrincipal OrionUserDetails principal,
                                 @PathVariable UUID id) {
        return SolicitudView.of(solicitudes.atender(id, principal.user().getId()));
    }

    /**
     * La autorización va en su casilla y se exige: el número se usa para una sola cosa, escribirle
     * sobre clases, y la persona tiene que haberlo dicho.
     */
    public record PedirLlamadaRequest(
            @NotBlank(message = "Dinos cómo te llamas.") @Size(max = 60) String firstName,
            @NotBlank(message = "Déjanos tu WhatsApp.") @Size(max = 30) String whatsapp,
            @AssertTrue(message = "Necesitamos tu autorización para escribirte.") boolean acceptsContact) {
    }

    public record Pedido(String firstName) {
    }

    public record SolicitudView(UUID id, String firstName, String whatsapp, ZonedDateTime createdAt,
                                ZonedDateTime attendedAt) {
        static SolicitudView of(CallbackRequest s) {
            return new SolicitudView(s.getId(), s.getFirstName(), s.getWhatsapp(),
                    ZonedDateTime.ofInstant(s.getCreatedAt(), BusinessZone.BOGOTA),
                    s.getAttendedAt() == null ? null
                            : ZonedDateTime.ofInstant(s.getAttendedAt(), BusinessZone.BOGOTA));
        }
    }
}
