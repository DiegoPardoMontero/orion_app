package co.orion.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.orion.shared.error.ServiceUnavailableException;

/**
 * El enlace con el que el admin abre el CV de un aspirante.
 *
 * <p>No se puede probar contra Cloudinary sin credenciales, así que lo que se fija aquí es lo que
 * sí depende de nosotros: que la firma sea SHA-1 hexadecimal sobre los parámetros en orden
 * alfabético con el {@code api_secret} al final —el mismo esquema que la subida, que sí está
 * verificada contra el servicio real— y que el enlace caduque.
 */
class CloudinaryDocumentStorageTest {

    private static final Instant AHORA = Instant.parse("2026-09-08T15:00:00Z");
    private static final String SECRETO = "un-secreto-de-prueba";
    private static final String KEY = "orion/documents/8f1c/cv";

    private final CloudinaryDocumentStorage storage = new CloudinaryDocumentStorage(
            Clock.fixed(AHORA, ZoneOffset.UTC),
            "cloudinary://123456789:" + SECRETO + "@sifqjzh1");

    @Test
    void theSignatureIsSha1OverTheParametersInAlphabeticalOrder() {
        Map<String, String> query = parametrosDe(
                storage.signedUrl(KEY, "application/pdf", Duration.ofMinutes(5)));

        long expira = AHORA.plus(Duration.ofMinutes(5)).getEpochSecond();
        String esperada = sha1Hex("expires_at=" + expira
                + "&format=pdf"
                + "&public_id=" + KEY
                + "&timestamp=" + AHORA.getEpochSecond()
                + "&type=authenticated"
                + SECRETO);

        assertThat(query).containsEntry("signature", esperada);
        // Sin api_key Cloudinary no sabe con qué secreto comprobar la firma.
        assertThat(query).containsEntry("api_key", "123456789");
        assertThat(query).containsEntry("type", "authenticated");
        assertThat(query).containsEntry("public_id", KEY);
    }

    @Test
    void theLinkExpiresWhenTheTimeToLiveSays() {
        Map<String, String> query = parametrosDe(
                storage.signedUrl(KEY, "application/pdf", Duration.ofMinutes(5)));

        assertThat(Long.parseLong(query.get("expires_at")))
                .isEqualTo(AHORA.plusSeconds(300).getEpochSecond());
    }

    /** Va al endpoint de descarga privada, que es el que no exige complementos. */
    @Test
    void theLinkPointsAtThePrivateDownloadEndpoint() {
        String url = storage.signedUrl(KEY, "application/pdf", Duration.ofMinutes(5));

        assertThat(url).startsWith("https://api.cloudinary.com/v1_1/sifqjzh1/image/download?");
        // Lo que rompía antes: mezclar la firma de entrega con los tokens de CDN privado.
        assertThat(url).doesNotContain("res.cloudinary.com").doesNotContain("~acl=").doesNotContain("s--");
    }

    /** El public_id lleva barras: se firma crudo y viaja codificado. */
    @Test
    void theSlashesInThePublicIdTravelEncoded() {
        String url = storage.signedUrl(KEY, "application/pdf", Duration.ofMinutes(5));

        assertThat(url).contains("public_id=orion%2Fdocuments%2F8f1c%2Fcv");
    }

    @ParameterizedTest
    @CsvSource({
        "application/pdf, pdf",
        "image/jpeg, jpg",
        "image/png, png",
        "image/webp, webp",
        "IMAGE/PNG, png",
    })
    void theFormatComesFromTheRealContentType(String contentType, String esperado) {
        assertThat(CloudinaryDocumentStorage.formatoDe(contentType)).isEqualTo(esperado);
    }

    /** Sin credenciales no se inventa un enlace roto: se dice que no se puede. */
    @Test
    void withoutCredentialsItSaysSoInsteadOfBuildingABrokenLink() {
        CloudinaryDocumentStorage sinCredenciales =
                new CloudinaryDocumentStorage(Clock.fixed(AHORA, ZoneOffset.UTC), "");

        assertThatThrownBy(() -> sinCredenciales.signedUrl(KEY, "application/pdf", Duration.ofMinutes(5)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    private static Map<String, String> parametrosDe(String url) {
        Map<String, String> params = new HashMap<>();
        String query = url.substring(url.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int sep = pair.indexOf('=');
            params.put(pair.substring(0, sep),
                    URLDecoder.decode(pair.substring(sep + 1), StandardCharsets.UTF_8));
        }
        return params;
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
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Lo que no se puede probar contra Cloudinary se prueba contra un servidor local que imita su
     * forma: el endpoint de descarga responde 302 hacia el CDN, y del CDN vuelven los bytes. Si el
     * cliente no siguiera la redirección, el previsualizador mostraría un documento vacío.
     */
    @Test
    void theDownloadFollowsTheRedirectToTheCdn() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] pdf = "%PDF-1.4 de prueba".getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1_1/sifqjzh1/image/download", ex -> {
            ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/cdn/archivo.pdf");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/cdn/archivo.pdf", ex -> {
            ex.getResponseHeaders().add("Content-Type", "application/pdf");
            ex.sendResponseHeaders(200, pdf.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(pdf);
            }
        });
        server.start();
        try {
            CloudinaryDocumentStorage local = new CloudinaryDocumentStorage(
                    Clock.fixed(AHORA, ZoneOffset.UTC),
                    "cloudinary://123456789:" + SECRETO + "@sifqjzh1",
                    "http://127.0.0.1:" + server.getAddress().getPort());

            assertThat(local.download(KEY, "application/pdf")).isEqualTo(pdf);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void theDownloadSaysSoWhenTheCdnFails() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        server.start();
        try {
            CloudinaryDocumentStorage local = new CloudinaryDocumentStorage(
                    Clock.fixed(AHORA, ZoneOffset.UTC),
                    "cloudinary://123456789:" + SECRETO + "@sifqjzh1",
                    "http://127.0.0.1:" + server.getAddress().getPort());

            assertThatThrownBy(() -> local.download(KEY, "application/pdf"))
                    .isInstanceOf(ServiceUnavailableException.class);
        } finally {
            server.stop(0);
        }
    }
}
