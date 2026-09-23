package co.orion.scheduling.api;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import co.orion.scheduling.application.ClassroomEvents;
import co.orion.scheduling.application.JaasProperties;
import co.orion.scheduling.application.JaasWebhookSignature;

/**
 * Los webhooks de JaaS: quién entra y sale de cada sala y cuánto habla.
 *
 * <p>Público y sin CSRF, como el de Wompi: lo llama 8x8, no un navegador. Lo que lo protege es la
 * firma, que se comprueba sobre el cuerpo tal cual llegó —por eso se recibe como texto y no como
 * objeto— y antes de tocar la base. Sin secreto configurado responde 503: no hay forma de saber
 * quién manda el evento, y procesarlo igual sería creerle a cualquiera.
 */
@RestController
public class JaasWebhookController {

    private static final Logger log = LoggerFactory.getLogger(JaasWebhookController.class);

    private final JaasProperties props;
    private final ClassroomEvents eventos;
    private final Clock clock;

    public JaasWebhookController(JaasProperties props, ClassroomEvents eventos, Clock clock) {
        this.props = props;
        this.eventos = eventos;
        this.clock = clock;
    }

    @PostMapping("/api/v1/webhooks/video/jaas")
    public ResponseEntity<Void> recibir(
            @RequestHeader(value = "X-Jaas-Signature", required = false) String firma,
            @RequestBody String cuerpo) {
        if (!props.webhooksConfigurados()) {
            return ResponseEntity.status(503).build();
        }
        if (!JaasWebhookSignature.valida(firma, cuerpo, props.webhookSecret(), clock.instant())) {
            log.warn("Webhook de JaaS con firma inválida o vencida; se rechaza.");
            return ResponseEntity.status(401).build();
        }
        eventos.procesar(cuerpo);
        return ResponseEntity.ok().build();
    }
}
