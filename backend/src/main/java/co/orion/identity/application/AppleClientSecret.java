package co.orion.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * El «client secret» de Sign in with Apple, que no es un secreto fijo sino un JWT firmado con la
 * llave privada de la cuenta de desarrollador (ES256), con la cuenta como emisor y el Services ID
 * como sujeto.
 *
 * <p>Se firma en cada intercambio y vive cinco minutos: Apple admite hasta seis meses, pero un
 * secreto que dura meses es uno que alguien acaba copiando. Firmar es barato.
 *
 * <p>ES256 en un JWT va en formato R‖S de 64 bytes, no en el DER que devuelve {@code SHA256withECDSA}
 * por defecto; {@code SHA256withECDSAinP1363Format} lo da directo. Mismo criterio que el minter de
 * JaaS: emitir un JWT sobre datos nuestros cabe en una clase y no justifica una dependencia.
 */
public class AppleClientSecret {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Duration VIDA = Duration.ofMinutes(5);

    private final String teamId;
    private final String keyId;
    private final String servicesId;
    private final PrivateKey llave;
    private final Clock clock;

    public AppleClientSecret(String teamId, String keyId, String servicesId, String privateKeyPem,
                             Clock clock) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.servicesId = servicesId;
        this.llave = cargar(privateKeyPem);
        this.clock = clock;
    }

    public String firmar() {
        Instant ahora = clock.instant();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "ES256");
        header.put("kid", keyId);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", teamId);
        claims.put("iat", ahora.getEpochSecond());
        claims.put("exp", ahora.plus(VIDA).getEpochSecond());
        claims.put("aud", "https://appleid.apple.com");
        claims.put("sub", servicesId);

        String firmable = b64(header) + "." + b64(claims);
        try {
            Signature firma = Signature.getInstance("SHA256withECDSAinP1363Format");
            firma.initSign(llave);
            firma.update(firmable.getBytes(StandardCharsets.UTF_8));
            return firmable + "." + B64.encodeToString(firma.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el secreto de Apple", ex);
        }
    }

    private static String b64(Map<String, Object> mapa) {
        try {
            return B64.encodeToString(JSON.writeValueAsBytes(mapa));
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo serializar el JWT de Apple", ex);
        }
    }

    /** El .p8 que entrega Apple: PKCS#8, en PEM de varias líneas o de una sola con {@code \n}. */
    private static PrivateKey cargar(String pem) {
        try {
            String base64 = pem.replace("\\n", "\n")
                    .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                    .replaceAll("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "La llave privada de Apple no se pudo leer: debe ser el .p8 (PKCS#8) de la cuenta.", ex);
        }
    }
}
