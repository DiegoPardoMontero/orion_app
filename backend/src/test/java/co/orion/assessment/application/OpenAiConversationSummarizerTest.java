package co.orion.assessment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.assessment.application.ConversationSummarizer.Pedido;
import co.orion.assessment.application.ConversationSummarizer.Turno;

/**
 * El resumen que escribe la IA, revisado antes de mostrarse. Sin red: se prueba lo que se le manda
 * y lo que se acepta de vuelta.
 */
class OpenAiConversationSummarizerTest {

    private final OpenAiConversationSummarizer resumidor = new OpenAiConversationSummarizer(
            "sk-test", "gpt-5-mini", "minimal", mock(AiUsageRecorder.class));

    @Test
    @DisplayName("Un resumen que habla de lo que contó pasa la revisión")
    void loQueContoPasa() {
        assertThat(OpenAiConversationSummarizer.aceptable(
                "Nos contaste que trabajas en logística y que necesitas el inglés para llamadas con "
                        + "proveedores, Ana. Vemos muchas posibilidades de que Orión te ayude con eso."))
                .isTrue();
    }

    @Test
    @DisplayName("Cualquier juicio sobre cómo habla, o la palabra examen o nivel, lo tumba")
    void elJuicioNoPasa() {
        assertThat(OpenAiConversationSummarizer.aceptable("Tu inglés es excelente, Ana.")).isFalse();
        assertThat(OpenAiConversationSummarizer.aceptable("Hablas muy bien para tu edad.")).isFalse();
        assertThat(OpenAiConversationSummarizer.aceptable("Tu nivel parece un B1.")).isFalse();
        assertThat(OpenAiConversationSummarizer.aceptable("No fue un examen, tranquila.")).isFalse();
        assertThat(OpenAiConversationSummarizer.aceptable("x".repeat(481))).isFalse();
        assertThat(OpenAiConversationSummarizer.aceptable("  ")).isFalse();
    }

    @Test
    @DisplayName("Se le quitan las comillas envolventes y los saltos de línea")
    void seLimpia() {
        assertThat(OpenAiConversationSummarizer.limpiar("«Nos contaste algo.\n\nY otra cosa.»"))
                .isEqualTo("Nos contaste algo. Y otra cosa.");
    }

    @Test
    @DisplayName("Al proveedor solo le llega el nombre de pila, los objetivos y la conversación")
    void loQueVeElProveedor() {
        Pedido pedido = new Pedido(UUID.randomUUID(), "Ana", List.of("Trabajo"),
                List.of(new Turno(true, "Hi Ana! How's your day?"),
                        new Turno(false, "Good, I'm at home."),
                        new Turno(false, "  ")));

        String conversacion = OpenAiConversationSummarizer.conversacion(pedido);

        assertThat(conversacion)
                .contains("Nombre de pila: Ana")
                .contains("Objetivos que marcó antes de empezar: Trabajo")
                .contains("M: Hi Ana! How's your day?")
                .contains("P: Good, I'm at home.")
                .doesNotContain("P:  ");
    }

    @Test
    @DisplayName("Pide razonamiento mínimo y pone tope a la salida")
    void elCuerpo() {
        Map<String, Object> cuerpo = resumidor.cuerpo(new Pedido(UUID.randomUUID(), "Ana",
                List.of(), List.of()));

        assertThat(cuerpo).containsEntry("model", "gpt-5-mini")
                .containsEntry("reasoning_effort", "minimal")
                .containsEntry("max_completion_tokens", 600);
    }
}
