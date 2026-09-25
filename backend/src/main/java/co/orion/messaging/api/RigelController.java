package co.orion.messaging.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.messaging.application.RigelService;
import co.orion.shared.security.OrionUserDetails;

/**
 * El hilo de Rigel: los mensajes oficiales de Orión, de solo lectura. Leerlo trae la bienvenida si
 * faltaba; marcarlo leído apaga su número en «Mensajes».
 */
@RestController
@RequestMapping("/api/v1/me/rigel")
public class RigelController {

    public record RigelButton(String label, String href) {
    }

    public record RigelMessageResponse(UUID id, String kind, String title, String body, List<RigelButton> buttons,
                                       Instant createdAt, boolean read) {
    }

    public record RigelThreadResponse(int unread, List<RigelMessageResponse> messages) {
    }

    private final RigelService rigel;

    public RigelController(RigelService rigel) {
        this.rigel = rigel;
    }

    @GetMapping
    public RigelThreadResponse hilo(@AuthenticationPrincipal OrionUserDetails principal) {
        RigelService.Hilo hilo = rigel.hilo(principal.user());
        return new RigelThreadResponse(hilo.noLeidos(), hilo.mensajes().stream()
                .map(l -> new RigelMessageResponse(
                        l.guardado().id(),
                        l.guardado().kind().name(),
                        l.mensaje().titulo(),
                        l.mensaje().cuerpo(),
                        l.mensaje().botones().stream().map(b -> new RigelButton(b.etiqueta(), b.ruta())).toList(),
                        l.guardado().createdAt(),
                        l.guardado().readAt() != null))
                .toList());
    }

    @PostMapping("/read")
    public ResponseEntity<Void> leidos(@AuthenticationPrincipal OrionUserDetails principal) {
        rigel.marcarLeidos(principal.user());
        return ResponseEntity.noContent().build();
    }
}
