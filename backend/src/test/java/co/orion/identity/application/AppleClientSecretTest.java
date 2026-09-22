package co.orion.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * El secreto de Apple, comprobado como lo comprueba Apple: verificando la firma ES256 con la llave
 * pública del par. El par se genera aquí; ninguna llave real de Orión toca el repositorio.
 */
class AppleClientSecretTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant AHORA = Instant.parse("2026-09-22T15:00:00Z");

    @Test
    @DisplayName("Firma ES256 verificable, con la cuenta como emisor y el Services ID como sujeto")
    void firmaYClaims() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair par = gen.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(par.getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----";

        String jwt = new AppleClientSecret("TEAM123", "KEY456", "co.orion.web", pem,
                Clock.fixed(AHORA, ZoneOffset.UTC)).firmar();
        String[] partes = jwt.split("\\.");

        Signature verificador = Signature.getInstance("SHA256withECDSAinP1363Format");
        verificador.initVerify(par.getPublic());
        verificador.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(verificador.verify(Base64.getUrlDecoder().decode(partes[2]))).isTrue();

        JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(partes[0]));
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(partes[1]));
        assertThat(header.get("alg").asText()).isEqualTo("ES256");
        assertThat(header.get("kid").asText()).isEqualTo("KEY456");
        assertThat(claims.get("iss").asText()).isEqualTo("TEAM123");
        assertThat(claims.get("sub").asText()).isEqualTo("co.orion.web");
        assertThat(claims.get("aud").asText()).isEqualTo("https://appleid.apple.com");
        // Cinco minutos, no los seis meses que Apple admite.
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(300);
    }
}
