package co.orion.notifications.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La identidad de Orión ante los servicios de push (VAPID, RFC 8292): un par de claves P-256 fijo y
 * un JWT firmado por envío. Sin las claves, {@link #disponible()} es falso y nadie ofrece avisos en
 * el dispositivo: la campana y el correo siguen igual.
 */
@Component
public class Vapid {

    private static final Logger log = LoggerFactory.getLogger(Vapid.class);

    private static final Duration VIDA_DEL_TOKEN = Duration.ofHours(12);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DE_B64 = Base64.getUrlDecoder();

    private final String publica;
    private final PrivateKey privada;
    private final String sujeto;
    private final Clock clock;

    public Vapid(@Value("${orion.push.vapid.public-key:}") String publica,
                 @Value("${orion.push.vapid.private-key:}") String privada,
                 @Value("${orion.push.vapid.subject:}") String sujeto,
                 Clock clock) {
        this.publica = publica == null ? "" : publica.trim();
        this.privada = leerPrivada(privada);
        this.sujeto = sujeto;
        this.clock = clock;
    }

    public boolean disponible() {
        return !publica.isEmpty() && privada != null;
    }

    /** La clave pública en base64url: el navegador la necesita para suscribirse. */
    public String clavePublica() {
        return publica;
    }

    /** La cabecera {@code Authorization} para un envío a ese origen (el del endpoint). */
    public String autorizacion(String origen) {
        long exp = clock.instant().plus(VIDA_DEL_TOKEN).getEpochSecond();
        String cabecera = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        String cuerpo = b64("{\"aud\":\"" + origen + "\",\"exp\":" + exp + ",\"sub\":\"" + sujeto + "\"}");
        String firmado = cabecera + "." + cuerpo;
        try {
            // P1363: la firma sale como r ‖ s, que es lo que pide JWS. El formato por defecto es DER.
            Signature firma = Signature.getInstance("SHA256withECDSAinP1363Format");
            firma.initSign(privada);
            firma.update(firmado.getBytes(StandardCharsets.US_ASCII));
            return "vapid t=" + firmado + "." + B64.encodeToString(firma.sign()) + ", k=" + publica;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el token VAPID", e);
        }
    }

    private static PrivateKey leerPrivada(String base64url) {
        if (base64url == null || base64url.isBlank()) {
            return null;
        }
        try {
            return WebPushCifrado.clavePrivada(DE_B64.decode(base64url.trim()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Mal copiada no tumba el arranque: los avisos en el dispositivo quedan apagados y
            // Sistema lo muestra, como cualquier otra integración.
            log.error("ORION_VAPID_PRIVATE_KEY no es una clave P-256 en base64url; avisos push apagados");
            return null;
        }
    }

    private static String b64(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
