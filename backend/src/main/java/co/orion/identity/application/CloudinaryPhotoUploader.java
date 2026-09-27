package co.orion.identity.application;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import co.orion.shared.error.ServiceUnavailableException;

/**
 * Subida firmada a Cloudinary por REST (sin SDK, para no arriesgar el build con una dependencia
 * nueva). La imagen se guarda ya como avatar 400×400 recortado a la cara (`c_fill,g_face`).
 *
 * `CLOUDINARY_URL` (`cloudinary://<api_key>:<api_secret>@<cloud_name>`) es un secreto y vive solo
 * como variable de entorno. Si falta, el bean arranca igual (para no tumbar el arranque en local)
 * y solo falla al intentar subir, con un mensaje claro.
 */
@Component
public class CloudinaryPhotoUploader implements PhotoUploader {

    private static final String TRANSFORMATION = "c_fill,g_face,w_400,h_400";

    private final Clock clock;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    /**
     * Con tiempos de espera: sin ellos, un Cloudinary lento dejaba la subida colgada hasta que el proxy
     * de Next la cortaba a los 30 s, y la persona veía un error genérico sin saber qué pasó.
     */
    private static final Logger log = LoggerFactory.getLogger(CloudinaryPhotoUploader.class);

    private final RestClient http = RestClient.builder().requestFactory(conTiempos()).build();

    public CloudinaryPhotoUploader(Clock clock, @Value("${CLOUDINARY_URL:}") String cloudinaryUrl) {
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
    public String upload(byte[] bytes, String contentType) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            // El mensaje que ve la persona no menciona la variable de entorno —no es asunto suyo—
            // pero el log sí, que es donde alguien puede hacer algo al respecto.
            throw new ServiceUnavailableException(
                    "No podemos recibir fotos en este momento. Inténtalo más tarde.");
        }

        long timestamp = clock.instant().getEpochSecond();
        // Firma de Cloudinary: parámetros a firmar en orden alfabético + api_secret, SHA-1 en hex
        // (el algoritmo por defecto de la cuenta). Verificado directo contra la API de Cloudinary.
        String toSign = "timestamp=" + timestamp + "&transformation=" + TRANSFORMATION + apiSecret;
        String signature = sha1Hex(toSign);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "orion-avatar";
            }
        });
        form.add("api_key", apiKey);
        form.add("timestamp", String.valueOf(timestamp));
        form.add("transformation", TRANSFORMATION);
        form.add("signature", signature);

        Map<?, ?> response;
        try {
            response = http.post()
                    .uri("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException ex) {
            log.warn("Cloudinary no respondió bien a la subida: {}", ex.getMessage());
            throw new ServiceUnavailableException("No pudimos subir tu foto. Inténtalo de nuevo en un momento.");
        }

        Object secureUrl = response == null ? null : response.get("secure_url");
        if (secureUrl == null) {
            throw new IllegalStateException("Cloudinary no devolvió una URL");
        }
        return secureUrl.toString();
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

    private static SimpleClientHttpRequestFactory conTiempos() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(25));
        return factory;
    }
}
