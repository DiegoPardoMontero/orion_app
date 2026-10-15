package co.orion.assessment.application;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.models.messages.OutputConfig;

import co.orion.assessment.domain.AiUsageOutcome;
import co.orion.shared.ai.ClaudeTexto;

/**
 * El resumen del diagnóstico con Claude Opus: lo que la persona contó y por qué Orión le sirve. Mismo
 * prompt, misma revisión de la salida y misma plantilla de respaldo que {@link OpenAiConversationSummarizer};
 * solo cambia quién escribe. El resultado nunca espera por esto más de lo que tarda la pantalla.
 */
@Component
@ConditionalOnProperty(name = "orion.ai.text-provider", havingValue = "claude")
public class ClaudeConversationSummarizer implements ConversationSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ClaudeConversationSummarizer.class);

    static final String NAME = "claude-resumen";
    private static final Duration CORTE = Duration.ofSeconds(25);

    private final ClaudeTexto claude;
    private final AiUsageRecorder uso;

    public ClaudeConversationSummarizer(ClaudeTexto claude, AiUsageRecorder uso) {
        this.claude = claude;
        this.uso = uso;
    }

    @Override
    public Optional<String> resumir(Pedido pedido) {
        if (!claude.disponible()) {
            return Optional.empty();
        }
        String modelo = claude.modeloDeCalidad();
        long inicio = System.nanoTime();
        ClaudeTexto.Respuesta respuesta;
        try {
            respuesta = claude.pedir(new ClaudeTexto.Pedido(modelo,
                    OpenAiConversationSummarizer.instrucciones(pedido.idioma()),
                    OpenAiConversationSummarizer.conversacion(pedido),
                    600, OutputConfig.Effort.LOW, false, CORTE));
        } catch (ClaudeTexto.NoRespondio ex) {
            uso.texto(pedido.actorId(), NAME, modelo, null, null, ms(inicio), AiUsageOutcome.TIMEOUT);
            log.warn("El resumen del diagnóstico no llegó a tiempo (Claude): {}", ex.getMessage());
            return Optional.empty();
        } catch (RuntimeException ex) {
            uso.texto(pedido.actorId(), NAME, modelo, null, null, ms(inicio), AiUsageOutcome.ERROR);
            log.warn("Claude falló al resumir el diagnóstico: {}", ex.getMessage());
            return Optional.empty();
        }
        Optional<String> texto = Optional.of(respuesta.texto())
                .map(OpenAiConversationSummarizer::limpiar)
                .filter(OpenAiConversationSummarizer::aceptable);
        uso.texto(pedido.actorId(), NAME, modelo, respuesta.entrada(), respuesta.salida(), ms(inicio),
                texto.isPresent() ? AiUsageOutcome.OK : AiUsageOutcome.INVALID_OUTPUT);
        return texto;
    }

    private static int ms(long inicio) {
        return (int) ((System.nanoTime() - inicio) / 1_000_000);
    }
}
