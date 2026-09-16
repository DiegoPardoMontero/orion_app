package co.orion.scheduling.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * El token que abre la sala, comprobado como lo comprobará 8x8: verificando la firma con la clave
 * pública del par.
 *
 * <p>El par se genera aquí mismo. Ni el test ni nadie en el repositorio toca la llave real de JaaS
 * —vive fuera, en {@code ~/.orion/jaas/}— y un test que dependiera de ella fallaría en cualquier
 * máquina que no sea la de Pardo.
 */
class JaasTokenMinterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String APP_ID = "vpaas-magic-cookie-0123456789abcdef";
    private static final String KEY_ID = APP_ID + "/a1b2c3";

    private static KeyPair par;
    private static JaasTokenMinter minter;

    @BeforeAll
    static void generarPar() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        par = gen.generateKeyPair();
        minter = new JaasTokenMinter(new JaasProperties(APP_ID, KEY_ID,
                Base64.getEncoder().encodeToString(par.getPrivate().getEncoded()), null));
    }

    private JsonNode parte(String token, int indice) throws Exception {
        return JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[indice]));
    }

    private String token(boolean moderador) {
        Instant ahora = Instant.parse("2026-09-15T14:00:00Z");
        return minter.mint("sala-1", "usuario-1", "Ana Ramírez", "ana@orion.test", null,
                moderador, ahora, ahora.plusSeconds(3600));
    }

    @Test
    @DisplayName("La firma se verifica con la clave pública del par")
    void laFirmaSeVerifica() throws Exception {
        String token = token(true);
        String[] partes = token.split("\\.");

        Signature verificador = Signature.getInstance("SHA256withRSA");
        verificador.initVerify(par.getPublic());
        verificador.update((partes[0] + "." + partes[1]).getBytes(StandardCharsets.UTF_8));

        assertThat(verificador.verify(Base64.getUrlDecoder().decode(partes[2]))).isTrue();
    }

    @Test
    @DisplayName("La cabecera lleva el kid y RS256, que es lo que JaaS exige")
    void laCabeceraEsLaQueEsperaJaas() throws Exception {
        JsonNode header = parte(token(true), 0);
        assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(header.get("typ").asText()).isEqualTo("JWT");
        assertThat(header.get("kid").asText()).isEqualTo(KEY_ID);
    }

    @Test
    @DisplayName("El cuerpo lleva aud, iss y sub tal como los espera JaaS")
    void elCuerpoEsElQueEsperaJaas() throws Exception {
        JsonNode p = parte(token(true), 1);
        assertThat(p.get("aud").asText()).isEqualTo("jitsi");
        assertThat(p.get("iss").asText()).isEqualTo("chat");
        assertThat(p.get("sub").asText()).isEqualTo(APP_ID);
        assertThat(p.get("room").asText()).isEqualTo("sala-1");
        assertThat(p.get("nbf").asLong()).isLessThan(p.get("exp").asLong());
    }

    @Test
    @DisplayName("El profesor manda en la sala y el estudiante no")
    void soloElProfesorEsModerador() throws Exception {
        // Es el motivo entero de haber dejado meet.jit.si: allí mandaba quien entrara primero, y un
        // estudiante podía silenciar o expulsar a su propio profesor.
        assertThat(parte(token(true), 1).get("context").get("user").get("moderator").asText())
                .isEqualTo("true");
        assertThat(parte(token(false), 1).get("context").get("user").get("moderator").asText())
                .isEqualTo("false");
    }

    @Test
    @DisplayName("Grabar y transcribir van apagados mientras no haya consentimiento")
    void lasFuncionesSensiblesVanApagadas() throws Exception {
        JsonNode features = parte(token(true), 1).get("context").get("features");
        assertThat(features.get("recording").asBoolean()).isFalse();
        assertThat(features.get("transcription").asBoolean()).isFalse();
        assertThat(features.get("livestreaming").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Sin credenciales el aula se apaga en vez de emitir un token roto")
    void sinCredencialesNoHayToken() {
        JaasTokenMinter apagado = new JaasTokenMinter(new JaasProperties(null, null, null, null));
        assertThat(apagado.disponible()).isFalse();
        assertThatThrownBy(() -> apagado.mint("s", "u", "n", "e", null, true,
                Instant.now(), Instant.now().plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JaaS no está configurado");
    }

    @Test
    @DisplayName("La llave se acepta con cabeceras PEM y con \\n literales de una variable de entorno")
    void elPemSeLimpiaAntesDeUsarse() {
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getMimeEncoder().encodeToString(par.getPrivate().getEncoded())
                        .replace(System.lineSeparator(), "\\n")
                + "\\n-----END PRIVATE KEY-----";
        JaasTokenMinter conPem = new JaasTokenMinter(new JaasProperties(APP_ID, KEY_ID, pem, null));

        assertThat(conPem.disponible()).isTrue();
        assertThat(conPem.mint("sala-1", "u", "n", "e", null, false,
                Instant.now(), Instant.now().plusSeconds(60)).split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("El dominio por defecto es el de JaaS, no el de la instalación pública")
    void elDominioPorDefectoEsJaas() {
        assertThat(new JaasProperties(APP_ID, KEY_ID, "x", null).domain()).isEqualTo("8x8.vc");
        assertThat(new JaasProperties(APP_ID, KEY_ID, "x", "  ").domain()).isEqualTo("8x8.vc");
    }
}
