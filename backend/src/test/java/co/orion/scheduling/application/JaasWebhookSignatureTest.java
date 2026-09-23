package co.orion.scheduling.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La firma de los webhooks de JaaS. Lo que se prueba es lo que impide que cualquiera nos cuente
 * quién entró a una clase: cuerpo alterado, firma de otro secreto, esquema distinto de v1 y una
 * firma vieja repetida.
 */
class JaasWebhookSignatureTest {

    private static final String SECRETO = "whsec_9635df66714a4cf088ee9d0979dd3bf6";
    private static final String CUERPO = "{\"eventType\":\"PARTICIPANT_JOINED\",\"idempotencyKey\":\"k1\"}";
    private static final Instant AHORA = Instant.parse("2026-09-22T20:00:00Z");

    private static String firmar(String llave, long t, String cuerpo) {
        return Base64.getEncoder().encodeToString(JaasWebhookSignature.hmac(llave, t + "." + cuerpo));
    }

    private static String cabecera(long t, String firma) {
        return "t=" + t + ",v1=" + firma;
    }

    @Test
    @DisplayName("Vale con el secreto entero y con lo que va después de whsec_")
    void lasDosLecturasDelSecreto() {
        long t = AHORA.getEpochSecond();
        assertThat(JaasWebhookSignature.valida(cabecera(t, firmar(SECRETO, t, CUERPO)), CUERPO, SECRETO, AHORA))
                .isTrue();
        assertThat(JaasWebhookSignature.valida(
                cabecera(t, firmar("9635df66714a4cf088ee9d0979dd3bf6", t, CUERPO)), CUERPO, SECRETO, AHORA))
                .isTrue();
    }

    @Test
    @DisplayName("Un cuerpo alterado, o firmado con otro secreto, no vale")
    void alteradoNoVale() {
        long t = AHORA.getEpochSecond();
        String firma = firmar(SECRETO, t, CUERPO);
        assertThat(JaasWebhookSignature.valida(cabecera(t, firma), CUERPO.replace("k1", "k2"), SECRETO, AHORA))
                .isFalse();
        assertThat(JaasWebhookSignature.valida(cabecera(t, firmar("whsec_otro", t, CUERPO)), CUERPO, SECRETO, AHORA))
                .isFalse();
    }

    @Test
    @DisplayName("Solo cuenta v1: otro esquema con la misma firma no vale")
    void soloV1() {
        long t = AHORA.getEpochSecond();
        assertThat(JaasWebhookSignature.valida("t=" + t + ",v0=" + firmar(SECRETO, t, CUERPO), CUERPO, SECRETO, AHORA))
                .isFalse();
    }

    @Test
    @DisplayName("Una firma de hace más de cinco minutos no vale: repetirla mañana no sirve")
    void firmaVieja() {
        long t = AHORA.minusSeconds(301).getEpochSecond();
        assertThat(JaasWebhookSignature.valida(cabecera(t, firmar(SECRETO, t, CUERPO)), CUERPO, SECRETO, AHORA))
                .isFalse();
    }

    @Test
    @DisplayName("Cabeceras rotas o vacías no revientan: simplemente no valen")
    void cabecerasRotas() {
        assertThat(JaasWebhookSignature.valida(null, CUERPO, SECRETO, AHORA)).isFalse();
        assertThat(JaasWebhookSignature.valida("basura", CUERPO, SECRETO, AHORA)).isFalse();
        assertThat(JaasWebhookSignature.valida("t=abc,v1=xyz", CUERPO, SECRETO, AHORA)).isFalse();
        assertThat(JaasWebhookSignature.valida("t=" + AHORA.getEpochSecond() + ",v1=@@@", CUERPO, SECRETO, AHORA))
                .isFalse();
        assertThat(JaasWebhookSignature.valida("t=1,v1=x", CUERPO, "", AHORA)).isFalse();
    }
}
