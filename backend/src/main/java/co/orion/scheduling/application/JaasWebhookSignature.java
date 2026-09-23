package co.orion.scheduling.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * La firma de los webhooks de JaaS: cabecera {@code X-Jaas-Signature: t=<segundos>,v1=<base64>},
 * donde v1 es HMAC-SHA256 de {@code "<t>.<cuerpo tal cual llegó>"} con el secreto del endpoint.
 *
 * <p>Solo cuenta el esquema v1 (la documentación pide ignorar los demás para impedir que alguien
 * fuerce uno más débil), la comparación es de tiempo constante y el {@code t} tiene que estar a
 * menos de cinco minutos de ahora: una firma válida capturada no sirve para repetir el evento
 * mañana. La repetición dentro de esa ventana la frena la llave de idempotencia.
 *
 * <p><strong>Sobre el secreto.</strong> La consola lo entrega como {@code whsec_…}. El ejemplo
 * resuelto de la documentación no reproduce su propia firma con ninguna lectura del secreto (con o
 * sin el prefijo, en texto o en hex), así que se aceptan las dos lecturas naturales —el texto
 * entero y lo que va después de {@code whsec_}—. Las dos salen del mismo secreto, así que no se
 * abre nada; el primer evento real dirá cuál usa 8x8, y el registro lo anota.
 */
public final class JaasWebhookSignature {

    static final Duration TOLERANCIA = Duration.ofMinutes(5);

    private JaasWebhookSignature() {
    }

    public static boolean valida(String cabecera, String cuerpo, String secreto, Instant ahora) {
        if (cabecera == null || cuerpo == null || secreto == null || secreto.isBlank()) {
            return false;
        }
        Long t = null;
        List<String> firmas = new ArrayList<>();
        for (String parte : cabecera.split(",")) {
            int igual = parte.indexOf('=');
            if (igual < 0) {
                continue;
            }
            String clave = parte.substring(0, igual).trim();
            String valor = parte.substring(igual + 1).trim();
            if ("t".equals(clave)) {
                try {
                    t = Long.parseLong(valor);
                } catch (NumberFormatException ex) {
                    return false;
                }
            } else if ("v1".equals(clave)) {
                firmas.add(valor);
            }
        }
        if (t == null || firmas.isEmpty()) {
            return false;
        }
        Instant firmado = Instant.ofEpochSecond(t);
        if (Duration.between(firmado, ahora).abs().compareTo(TOLERANCIA) > 0) {
            return false;
        }
        String firmable = t + "." + cuerpo;
        for (String llave : lecturasDelSecreto(secreto)) {
            byte[] esperada = hmac(llave, firmable);
            for (String recibida : firmas) {
                byte[] bytes;
                try {
                    bytes = Base64.getDecoder().decode(recibida);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (MessageDigest.isEqual(esperada, bytes)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> lecturasDelSecreto(String secreto) {
        String limpio = secreto.trim();
        return limpio.startsWith("whsec_")
                ? List.of(limpio, limpio.substring("whsec_".length()))
                : List.of(limpio);
    }

    static byte[] hmac(String llave, String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(llave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC no disponible", ex);
        }
    }
}
