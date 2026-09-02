package co.orion.billing.api;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.orion.billing.application.InvalidWebhookSignatureException;
import co.orion.billing.application.PaymentWebhookService;

/**
 * La entrada de los eventos de Wompi. Es público por necesidad —lo llama Wompi, no un navegador— y
 * por eso está exento de CSRF y de sesión: lo que lo protege no es la autenticación sino la firma,
 * que se verifica antes de tocar la base.
 *
 * El cuerpo se recibe como String crudo, no como objeto: el checksum se calcula sobre lo que llegó,
 * y deserializar y volver a serializar cambiaría bytes (orden de claves, espacios) y rompería la
 * verificación.
 *
 * Un evento ya procesado responde 200: para la pasarela es un éxito y deja de reintentarlo, que es
 * exactamente lo que queremos.
 */
@RestController
@RequestMapping("/api/v1/webhooks/payments")
public class WompiWebhookController {

    private final PaymentWebhookService webhooks;

    public WompiWebhookController(PaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/wompi")
    public ResponseEntity<Map<String, String>> receive(
            @RequestBody String rawBody,
            @RequestHeader Map<String, String> headers) {
        try {
            boolean processed = webhooks.handle(rawBody, new HashMap<>(headers));
            return ResponseEntity.ok(Map.of("status", processed ? "processed" : "duplicate"));
        } catch (InvalidWebhookSignatureException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid_signature"));
        }
    }
}
