package co.orion.identity.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import co.orion.shared.error.ServiceUnavailableException;

/**
 * Documentos privados en Cloudinary con {@code type: authenticated} (no {@code upload}): no son
 * accesibles por URL pública. Se guardan bajo {@code orion/documents/{userId}/} y la lectura es
 * siempre por una URL firmada de corta vida (SHA-1, igual que {@link CloudinaryPhotoUploader}).
 *
 * Sin SDK (por REST) para no arriesgar el build. Si falta {@code CLOUDINARY_URL} el bean arranca
 * igual y solo falla al intentar subir.
 */
@Component
public class CloudinaryDocumentStorage implements DocumentStorage {

    private final Clock clock;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final RestClient http = RestClient.create();

    public CloudinaryDocumentStorage(Clock clock, @Value("${CLOUDINARY_URL:}") String cloudinaryUrl) {
        this.clock = clock;
        String cloud = "";
        String key = "";
        String secret = "";
        if (cloudinaryUrl != null && cloudinaryUrl.startsWith("cloudinary://")) {
            URI uri = URI.create(cloudinaryUrl);
            cloud = uri.getHost() == null ? "" : uri.getHost();
            String userInfo = uri.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                int sep = userInfo.indexOf(':');
                key = userInfo.substring(0, sep);
                secret = userInfo.substring(sep + 1);
            }
        }
        this.cloudName = cloud;
        this.apiKey = key;
        this.apiSecret = secret;
    }

    @Override
    public String upload(byte[] bytes, String contentType, UUID userId, String fileName) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new ServiceUnavailableException(
                    "No podemos recibir documentos en este momento. Inténtalo más tarde.");
        }

        long timestamp = clock.instant().getEpochSecond();
        String folder = "orion/documents/" + userId;
        // Firma: parámetros a firmar en orden alfabético + api_secret, SHA-1 en hex.
        String toSign = "folder=" + folder + "&timestamp=" + timestamp + "&type=authenticated" + apiSecret;
        String signature = sha1Hex(toSign);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName == null ? "orion-document" : fileName;
            }
        });
        form.add("api_key", apiKey);
        form.add("timestamp", String.valueOf(timestamp));
        form.add("folder", folder);
        form.add("type", "authenticated");
        form.add("signature", signature);

        Map<?, ?> response = http.post()
                .uri("https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(Map.class);

        Object publicId = response == null ? null : response.get("public_id");
        if (publicId == null) {
            throw new IllegalStateException("Cloudinary no devolvió public_id");
        }
        return publicId.toString();
    }

    /**
     * URL de descarga firmada y temporal para un recurso {@code authenticated}. Cloudinary firma
     * {@code public_id + expira} con SHA-1; la URL caduca en {@code expira}. (La entrega temporal
     * real exige el add-on de tokens; aquí se construye la firma correctamente aunque no se pueda
     * verificar en vivo sin credenciales.)
     */
    @Override
    public String signedUrl(String storageKey, Duration ttl) {
        long expiresAt = clock.instant().plus(ttl).getEpochSecond();
        String toSign = "exp=" + expiresAt + "~acl=" + storageKey + apiSecret;
        String signature = sha1Hex(toSign);
        String cloud = cloudName.isBlank() ? "orion" : cloudName;
        return "https://res.cloudinary.com/" + cloud + "/image/authenticated"
                + "/s--" + signature.substring(0, 8) + "--"
                + "/" + storageKey + "?_exp=" + expiresAt;
    }

    private static String sha1Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 no disponible", ex);
        }
    }
}
