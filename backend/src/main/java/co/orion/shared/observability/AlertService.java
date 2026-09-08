package co.orion.shared.observability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.orion.shared.mail.MailTransport;
import co.orion.shared.mail.OutgoingEmail;
import co.orion.shared.time.BusinessZone;

/**
 * Manda las alertas de operación al correo de quien lleva Orión.
 *
 * <p>Sin servicio externo: el correo ya funciona, ya tiene marca y ya llega. Añadir un Sentry para
 * decir lo que un correo puede decir sería una dependencia más que mantener, y una cuenta más que
 * alguien tendría que acordarse de mirar.
 *
 * <p><strong>Qué NO va en el correo:</strong> cuerpos de petición, cookies ni cabeceras de
 * autorización. Una alerta que arrastra el cuerpo de un POST acaba llevando una contraseña a una
 * bandeja de Gmail, y entonces el sistema de alertas es el incidente.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final MailTransport transport;
    private final Clock clock;
    private final String destinatario;
    private final boolean habilitado;
    private final AlertThrottle throttle;

    public AlertService(MailTransport transport,
                        Clock clock,
                        @Value("${orion.alerts.to:}") String destinatario,
                        @Value("${orion.alerts.enabled:true}") boolean habilitado,
                        @Value("${orion.alerts.per-signature-minutes:60}") int minutosPorFirma,
                        @Value("${orion.alerts.daily-cap:50}") int topeDiario) {
        this.transport = transport;
        this.clock = clock;
        this.destinatario = destinatario == null ? "" : destinatario.trim();
        this.habilitado = habilitado;
        this.throttle = new AlertThrottle(
                Duration.ofMinutes(minutosPorFirma), topeDiario, BusinessZone.BOGOTA);
    }

    /**
     * Manda una alerta si el freno lo permite. Nunca lanza: una alerta que falla no puede tumbar la
     * operación que la disparó — sería exactamente al revés de para lo que existe.
     */
    public void alert(String firma, String asunto, String cuerpo) {
        if (!habilitado || destinatario.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        if (!throttle.deberiaEnviar(firma, now)) {
            log.debug("Alerta «{}» silenciada por el freno", firma);
            return;
        }

        String pie = "\n\n—\nAlerta " + throttle.enviadasHoy(now) + " de "
                + throttle.topeDiario() + " hoy. Las repetidas de la misma firma se agrupan.";
        try {
            transport.send(OutgoingEmail.plain(destinatario, "[Orión] " + asunto,
                    cuerpo + pie, "<pre>" + escape(cuerpo + pie) + "</pre>"));
        } catch (Exception ex) {
            log.warn("No se pudo enviar la alerta «{}»: {}", firma, ex.getMessage());
        }
    }

    /**
     * La firma de un error: clase de la excepción más el primer frame que sea nuestro.
     *
     * <p>Agrupar por mensaje no serviría —los mensajes llevan ids y fechas y cada uno sería único—
     * y agrupar solo por clase juntaría cosas que no tienen nada que ver. El primer frame de
     * {@code co.orion} es lo que de verdad identifica el fallo.
     */
    public static String firmaDe(Throwable ex) {
        String origen = Arrays.stream(ex.getStackTrace())
                .filter(f -> f.getClassName().startsWith("co.orion"))
                .findFirst()
                .map(f -> f.getClassName() + ":" + f.getLineNumber())
                .orElse("desconocido");
        return ex.getClass().getSimpleName() + "@" + origen;
    }

    /** La traza recortada: las primeras líneas dicen dónde; el resto es ruido de framework. */
    public static String trazaCorta(Throwable ex, int lineas) {
        StringBuilder sb = new StringBuilder(ex.toString());
        StackTraceElement[] frames = ex.getStackTrace();
        for (int i = 0; i < Math.min(lineas, frames.length); i++) {
            sb.append("\n    at ").append(frames[i]);
        }
        if (frames.length > lineas) {
            sb.append("\n    … ").append(frames.length - lineas).append(" más");
        }
        return sb.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
