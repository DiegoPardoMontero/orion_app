package co.orion.assessment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.assessment.domain.AiUsageOutcome;
import co.orion.shared.ai.ClaudeTexto;
import co.orion.support.ServidorDePrueba;

/** La traducción de Meissa con Claude Haiku contra un proveedor de mentira: bien, igual, lenta, rota. */
class ClaudeTraductorDeFrasesTest {

    private final ServidorDePrueba proveedor = new ServidorDePrueba();
    private final AiUsageRecorder uso = mock(AiUsageRecorder.class);
    private final ClaudeTexto claude = new ClaudeTexto("sk-ant-test", proveedor.url(""), "claude-opus-5-5", "claude-haiku-5-5");
    private final ClaudeTraductorDeFrases traductor = new ClaudeTraductorDeFrases(claude, uso, 1);

    @AfterEach
    void apagar() {
        proveedor.close();
    }

    @Test
    @DisplayName("Traduce con el modelo rápido, sin razonamiento, y deja su fila OK")
    void traduce() {
        proveedor.responde(200, ServidorDePrueba.claude("¿Cuál fue el último error difícil que arreglaste?"));

        assertThat(traductor.alEspanol(null, "What was the last tricky bug you fixed?"))
                .contains("¿Cuál fue el último error difícil que arreglaste?");
        verify(uso).texto(any(), eq(ClaudeTraductorDeFrases.NAME), eq("claude-haiku-5-5"), eq(100), eq(20), anyInt(),
                eq(AiUsageOutcome.OK));
        String cuerpo = proveedor.cuerpos().getFirst();
        assertThat(cuerpo).contains("\"model\":\"claude-haiku-5-5\"").contains("\"type\":\"disabled\"")
                .contains("\"effort\":\"low\"").contains("Meissa");
    }

    @Test
    void igualNoSeMuestra() {
        proveedor.responde(200, ServidorDePrueba.claude("Sigamos en español, que así me cuentas mejor."));

        assertThat(traductor.alEspanol(null, "Sigamos en espanol, que asi me cuentas mejor")).isEmpty();
    }

    @Test
    @DisplayName("Lenta: se corta, sin traducción, y la fila dice TIMEOUT")
    void lenta() {
        proveedor.tarda(2_500).responde(200, ServidorDePrueba.claude("tarde"));

        assertThat(traductor.alEspanol(null, "Hi!")).isEmpty();
        verify(uso).texto(any(), any(), any(), eq(null), eq(null), anyInt(), eq(AiUsageOutcome.TIMEOUT));
    }

    @Test
    @DisplayName("Rota (4xx): sin traducción, la fila dice ERROR y la conversación sigue")
    void rota() {
        proveedor.responde(400, "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"boom\"}}");

        assertThat(traductor.alEspanol(null, "Hi!")).isEmpty();
        verify(uso).texto(any(), any(), any(), eq(null), eq(null), anyInt(), eq(AiUsageOutcome.ERROR));
    }

    @Test
    @DisplayName("Sin llave, no llama a nadie")
    void sinLlave() {
        ClaudeTraductorDeFrases apagado = new ClaudeTraductorDeFrases(
                new ClaudeTexto("", "", "claude-opus-5-5", "claude-haiku-5-5"), uso, 1);

        assertThat(apagado.alEspanol(null, "Hi!")).isEmpty();
        assertThat(proveedor.llamadas()).isZero();
    }
}
