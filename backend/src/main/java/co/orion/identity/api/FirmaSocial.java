package co.orion.identity.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * La firma de lo que el login social deja en cookies: la solicitud que espera a que la persona
 * vuelva del proveedor y el alta a medio completar.
 *
 * <p><strong>La llave es estable</strong> (24/09/2026). Antes nacía al azar con cada arranque y la
 * solicitud de Google vivía en la sesión en memoria: cada despliegue —varios al día— dejaba sin
 * salida a quien estuviera en ese momento en la pantalla de Google, con el genérico «No pudimos
 * entrar». Ahora sale de {@code orion.social.state-secret} si está, y si no, de los secretos de los
 * proveedores configurados, que ya son secretos y no cambian entre arranques. Solo sin ningún
 * proveedor —cuando no hay login social que firmar— es al azar.
 *
 * <p>Nunca se deserializa con la serialización de Java: el contenido viene del navegador. Es JSON
 * de campos de texto, y cualquier cosa rara es «no hay nada», nunca un 500.
 */
@Component
class FirmaSocial {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final byte[] llave;

    FirmaSocial(@Value("${orion.social.state-secret:}") String secreto,
                @Value("${orion.social.google.client-secret:}") String google,
                @Value("${orion.social.microsoft.client-secret:}") String microsoft,
                @Value("${orion.social.facebook.client-secret:}") String facebook,
                @Value("${orion.social.apple.private-key:}") String apple) {
        String base = !secreto.isBlank() ? secreto : google + "|" + microsoft + "|" + facebook + "|" + apple;
        if (base.replace("|", "").isBlank()) {
            this.llave = new byte[32];
            new SecureRandom().nextBytes(llave);
        } else {
            this.llave = sha256("orion-login-social|" + base);
        }
    }

    /** {@code cuerpo.firma}, los dos en base64url. */
    String empaquetar(Object contenido) {
        try {
            String cuerpo = Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(contenido));
            return cuerpo + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(cuerpo));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el login social", ex);
        }
    }

    /** Lo empaquetado, si la firma cuadra; {@code null} ante cualquier cosa rara. */
    <T> T desempaquetar(String valor, Class<T> tipo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        int punto = valor.lastIndexOf('.');
        if (punto < 0) {
            return null;
        }
        try {
            String cuerpo = valor.substring(0, punto);
            byte[] firma = Base64.getUrlDecoder().decode(valor.substring(punto + 1));
            if (!MessageDigest.isEqual(firma, hmac(cuerpo))) {
                return null;
            }
            return JSON.readValue(Base64.getUrlDecoder().decode(cuerpo), tipo);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Un nombre corto y estable para la cookie de una solicitud, sacado de su {@code state}. */
    static String huella(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(texto)).substring(0, 16);
    }

    private byte[] hmac(String contenido) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(llave, "HmacSHA256"));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC no disponible", ex);
        }
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
