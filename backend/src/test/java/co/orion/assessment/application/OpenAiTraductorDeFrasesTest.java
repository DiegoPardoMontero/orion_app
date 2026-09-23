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
import co.orion.support.ServidorDePrueba;

/** La traducción de Meissa contra un proveedor de mentira: bien, lenta, rota o devolviendo lo mismo. */
class OpenAiTraductorDeFrasesTest {

    private final ServidorDePrueba proveedor = new ServidorDePrueba();
    private final AiUsageRecorder uso = mock(AiUsageRecorder.class);
    private final OpenAiTraductorDeFrases traductor = new OpenAiTraductorDeFrases("sk-test", "gpt-4.1-nano", "",
            proveedor.url("/v1/chat/completions"), 1, uso);

    @AfterEach
    void apagar() {
        proveedor.close();
    }

    @Test
    @DisplayName("Responde bien: la traducción, su fila OK, y sin razonamiento para el modelo nano")
    void traduce() {
        proveedor.responde(200, ServidorDePrueba.chat("¿Cuál fue el último error difícil que arreglaste?"));

        assertThat(traductor.alEspanol(null, "What was the last tricky bug you fixed?"))
                .contains("¿Cuál fue el último error difícil que arreglaste?");
        verify(uso).texto(any(), eq(OpenAiTraductorDeFrases.NAME), eq("gpt-4.1-nano"), eq(100), eq(20), anyInt(),
                eq(AiUsageOutcome.OK));
        assertThat(proveedor.cuerpos().getFirst()).contains("gpt-4.1-nano").doesNotContain("reasoning_effort");
    }

    @Test
    @DisplayName("Si vuelve la misma frase, no hay nada que mostrar debajo")
    void igualNoSeMuestra() {
        proveedor.responde(200, ServidorDePrueba.chat("Sigamos en español, que así me cuentas mejor."));

        assertThat(traductor.alEspanol(null, "Sigamos en espanol, que asi me cuentas mejor")).isEmpty();
    }

    @Test
    @DisplayName("Lenta: se corta, sin traducción, y la fila dice TIMEOUT")
    void lenta() {
        proveedor.tarda(2_500).responde(200, ServidorDePrueba.chat("tarde"));

        assertThat(traductor.alEspanol(null, "Hi!")).isEmpty();
        verify(uso).texto(any(), any(), any(), eq(null), eq(null), anyInt(), eq(AiUsageOutcome.TIMEOUT));
    }

    @Test
    @DisplayName("Rota: sin traducción, la fila dice ERROR y la conversación sigue")
    void rota() {
        proveedor.responde(500, "{\"error\":\"boom\"}");

        assertThat(traductor.alEspanol(null, "Hi!")).isEmpty();
        verify(uso).texto(any(), any(), any(), eq(null), eq(null), anyInt(), eq(AiUsageOutcome.ERROR));
    }
}
