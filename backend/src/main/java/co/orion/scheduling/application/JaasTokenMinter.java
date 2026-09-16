package co.orion.scheduling.application;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Firma los JWT que dejan entrar a una sala de JaaS.
 *
 * <p><strong>Por qué a mano y sin librería.</strong> Un JWT firmado es tres bloques en base64url
 * unidos por puntos, y el último es una firma RSA de los dos primeros: cabe en esta clase. La
 * asimetría importa — <em>verificar</em> un token a mano sí sería temerario, porque implica
 * interpretar entrada ajena y hay una lista larga de formas de hacerlo mal (algoritmo {@code none},
 * confusión de tipo de clave, comparaciones no constantes). Aquí solo emitimos, sobre datos
 * nuestros, y quien verifica es 8x8. Traer una dependencia para treinta líneas de emisión sería
 * peor trato.
 *
 * <p>La clave se carga una vez, al construir. Si el PEM no sirve, la aplicación no arranca: es
 * mejor enterarse en el despliegue que cuando un estudiante pulsa «entrar a clase».
 */
@Component
public class JaasTokenMinter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final JaasProperties props;
    private final PrivateKey privateKey;

    public JaasTokenMinter(JaasProperties props) {
        this.props = props;
        this.privateKey = props.configurado() ? cargar(props.privateKeyBase64()) : null;
    }

    public boolean disponible() {
        return privateKey != null;
    }

    /**
     * Un token para que una persona entre a una sala.
     *
     * @param room      nombre de sala SIN el prefijo del AppID; JaaS lo compone aparte
     * @param moderator si puede silenciar, expulsar y terminar la reunión. El profesor sí; el
     *                  estudiante no. Es el motivo entero de haber dejado la sala pública.
     * @param expiresAt cuándo deja de servir. Corto a propósito: un token de sala es una llave, y
     *                  una llave que no caduca acaba circulando por WhatsApp.
     */
    public String mint(String room, String userId, String name, String email, String avatar,
                       boolean moderator, Instant now, Instant expiresAt) {
        if (!disponible()) {
            throw new IllegalStateException(
                    "JaaS no está configurado: falta orion.jaas.app-id, key-id o private-key.");
        }

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "RS256");
        header.put("kid", props.keyId());
        header.put("typ", "JWT");

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId);
        user.put("name", name);
        user.put("email", email);
        user.put("avatar", avatar);
        user.put("moderator", Boolean.toString(moderator));

        // Todo apagado salvo lo que se use. Grabar o transcribir una clase de idiomas es tratar
        // datos personales de dos personas: se enciende cuando exista el consentimiento, no antes.
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("livestreaming", false);
        features.put("recording", false);
        features.put("transcription", false);
        features.put("outbound-call", false);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("user", user);
        context.put("features", features);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("aud", "jitsi");
        payload.put("iss", "chat");
        payload.put("sub", props.appId());
        payload.put("room", room);
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("nbf", now.getEpochSecond());
        payload.put("context", context);

        String firmable = b64(header) + "." + b64(payload);
        return firmable + "." + B64.encodeToString(firmar(firmable));
    }

    private String b64(Map<String, Object> mapa) {
        try {
            return B64.encodeToString(JSON.writeValueAsBytes(mapa));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar el JWT de JaaS", ex);
        }
    }

    private byte[] firmar(String contenido) {
        try {
            Signature firma = Signature.getInstance("SHA256withRSA");
            firma.initSign(privateKey);
            firma.update(contenido.getBytes(StandardCharsets.UTF_8));
            return firma.sign();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el JWT de JaaS", ex);
        }
    }

    private static PrivateKey cargar(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "La llave privada de JaaS no se pudo leer. Debe ser PKCS#8 "
                            + "(-----BEGIN PRIVATE KEY-----), no PKCS#1.", ex);
        }
    }
}
