package co.orion.notifications.application;

import java.net.URI;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.messaging.domain.NotificationCreatedEvent;
import co.orion.notifications.persistence.PushSubscriptions;

/**
 * Los avisos que además suenan en el celular o el escritorio (24/09/2026: «recordar de clases,
 * recordar calificar… sin ser demasiado invasivo»). No todo lo que entra a la campana: solo lo que
 * pide hacer algo pronto o lo que la persona está esperando. Un logro, un aviso de la ficha o la
 * confirmación de algo que acaba de hacer ella misma se quedan en la campana.
 *
 * <p>Sale después del commit y en otro hilo: un servicio de push lento no retiene a nadie. Una
 * suscripción que responde 404/410 murió (revocó el permiso, borró el navegador) y se borra.
 */
@Component
public class AvisosEnElDispositivo {

    private static final Logger log = LoggerFactory.getLogger(AvisosEnElDispositivo.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    /** Lo que suena. El resto, solo en la campana. */
    static final Set<String> SUENAN = Set.of(
            "CLASS_SOON",        // una hora antes
            "RATE_REMINDER",     // al día siguiente, si no calificó
            "MESSAGE",           // alguien te escribió
            "BOOKING_RECEIVED",  // al profe: una clase nueva
            "BOOKING_CANCELLED", // a quien no canceló
            "PRACTICE_READY",
            "SUPPORT_ANSWERED",
            "PAYOUT_PAID");

    private final PushSubscriptions suscripciones;
    private final PushGateway gateway;
    private final Vapid vapid;
    private final Clock clock;

    public AvisosEnElDispositivo(PushSubscriptions suscripciones, PushGateway gateway, Vapid vapid, Clock clock) {
        this.suscripciones = suscripciones;
        this.gateway = gateway;
        this.vapid = vapid;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(NotificationCreatedEvent event) {
        if (!vapid.disponible() || !SUENAN.contains(event.type())) {
            return;
        }
        enviar(event.userId(), event.title(), event.body(), event.linkPath(), event.type());
    }

    /** @return a cuántos navegadores llegó */
    public int enviar(UUID userId, String titulo, String cuerpo, String enlace, String etiqueta) {
        byte[] mensaje;
        try {
            mensaje = JSON.writeValueAsBytes(Map.of(
                    "title", titulo, "body", cuerpo == null ? "" : cuerpo,
                    "url", enlace == null ? "/" : enlace, "tag", etiqueta));
        } catch (JsonProcessingException e) {
            return 0;
        }
        int llegaron = 0;
        for (PushSubscriptions.Suscripcion s : suscripciones.de(userId)) {
            try {
                byte[] cifrado = WebPushCifrado.cifrar(B64.decode(s.p256dh()), B64.decode(s.auth()), mensaje);
                int estado = gateway.enviar(s.endpoint(), cifrado, Map.of(
                        "TTL", "86400",
                        "Urgency", "normal",
                        "Content-Encoding", "aes128gcm",
                        "Content-Type", "application/octet-stream",
                        "Authorization", vapid.autorizacion(origen(s.endpoint()))));
                if (estado == 404 || estado == 410) {
                    suscripciones.borrarMuerta(s.id());
                } else if (estado >= 200 && estado < 300) {
                    suscripciones.usada(s.id(), clock.instant());
                    llegaron++;
                } else {
                    log.warn("El servicio de push respondió {} para una suscripción de {}", estado, userId);
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo enviar un aviso push a {}: {}", userId, ex.getMessage());
            }
        }
        return llegaron;
    }

    private static String origen(String endpoint) {
        URI u = URI.create(endpoint);
        return u.getScheme() + "://" + u.getHost() + (u.getPort() == -1 ? "" : ":" + u.getPort());
    }
}
