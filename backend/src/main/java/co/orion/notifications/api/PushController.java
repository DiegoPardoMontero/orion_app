package co.orion.notifications.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.orion.notifications.application.AvisosEnElDispositivo;
import co.orion.notifications.application.Vapid;
import co.orion.notifications.persistence.PushSubscriptions;
import co.orion.shared.error.UnprocessableException;
import co.orion.shared.security.OrionUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Suscribir este navegador a los avisos en el dispositivo, apagarlos y probarlos.
 *
 * <p>El servidor hace un POST a la URL que registra el navegador, así que no se acepta cualquiera:
 * solo {@code https} y solo hacia los servicios de push de los navegadores. Sin esa lista, cualquier
 * cuenta podría hacer que Orión llamara a una dirección interna (SSRF).
 */
@RestController
public class PushController {

    /** Los servicios de push de Chrome/Edge/Android, Firefox, Windows y Safari. */
    private static final List<String> SERVICIOS = List.of(
            "fcm.googleapis.com", ".push.services.mozilla.com", ".notify.windows.com", ".push.apple.com");

    public record PushConfigResponse(boolean enabled, String publicKey) {
    }

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }

    public record SubscribeRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Keys keys) {
    }

    public record UnsubscribeRequest(@NotBlank @Size(max = 1000) String endpoint) {
    }

    public record TestPushResponse(int devices) {
    }

    private final Vapid vapid;
    private final PushSubscriptions suscripciones;
    private final AvisosEnElDispositivo avisos;

    public PushController(Vapid vapid, PushSubscriptions suscripciones, AvisosEnElDispositivo avisos) {
        this.vapid = vapid;
        this.suscripciones = suscripciones;
        this.avisos = avisos;
    }

    @GetMapping("/api/v1/push/config")
    public PushConfigResponse config() {
        return new PushConfigResponse(vapid.disponible(), vapid.disponible() ? vapid.clavePublica() : null);
    }

    @PostMapping("/api/v1/me/push-subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suscribir(@AuthenticationPrincipal OrionUserDetails principal,
                          @Valid @RequestBody SubscribeRequest body,
                          @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        if (!vapid.disponible()) {
            throw new UnprocessableException("Los avisos en el dispositivo no están activos en Orión todavía.");
        }
        exigirServicioConocido(body.endpoint());
        suscripciones.guardar(principal.user().getId(), body.endpoint(), body.keys().p256dh(), body.keys().auth(),
                userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300)));
    }

    /** Un POST y no un DELETE con cuerpo: el endpoint es largo y no cabe bien en la ruta. */
    @PostMapping("/api/v1/me/push-subscriptions/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagar(@AuthenticationPrincipal OrionUserDetails principal,
                       @Valid @RequestBody UnsubscribeRequest body) {
        suscripciones.borrar(principal.user().getId(), body.endpoint());
    }

    /** «Probar»: un aviso a todos tus navegadores suscritos, para ver que de verdad suena. */
    @PostMapping("/api/v1/me/push-subscriptions/test")
    public TestPushResponse probar(@AuthenticationPrincipal OrionUserDetails principal) {
        return new TestPushResponse(avisos.enviar(principal.user().getId(), "Así se ven los avisos de Orión ✦",
                "Te avisaremos una hora antes de cada clase y cuando algo te espere.", "/", "TEST"));
    }

    static void exigirServicioConocido(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException ex) {
            throw new UnprocessableException("Esa suscripción no es válida.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean conocido = "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && SERVICIOS.stream().anyMatch(s -> s.startsWith(".") ? host.endsWith(s) : host.equals(s));
        if (!conocido) {
            throw new UnprocessableException("Esa suscripción no viene de un servicio de avisos conocido.");
        }
    }
}
