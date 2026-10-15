package co.orion.assessment.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.models.messages.OutputConfig;

import co.orion.assessment.domain.AiUsageOutcome;
import co.orion.shared.ai.ClaudeTexto;

/**
 * La traducción en vivo de lo que dice Meissa, con Claude Haiku: rápida o nada. El corte sigue siendo
 * de cinco segundos y sin reintento; el razonamiento va apagado porque pensar es tardar. El prompt y
 * la regla «si vuelve lo mismo, no se muestra» son los de {@link OpenAiTraductorDeFrases}.
 */
@Component
@ConditionalOnProperty(name = "orion.ai.text-provider", havingValue = "claude")
public class ClaudeTraductorDeFrases implements TraductorDeFrases {

    private static final Logger log = LoggerFactory.getLogger(ClaudeTraductorDeFrases.class);

    static final String NAME = "claude-traduccion";

    private final ClaudeTexto claude;
    private final AiUsageRecorder uso;
    private final Duration corte;

    public ClaudeTraductorDeFrases(ClaudeTexto claude,
                                   AiUsageRecorder uso,
                                   @Value("${orion.assessment.translation.timeout-seconds:5}") int corteSegundos) {
        this.claude = claude;
        this.uso = uso;
        this.corte = Duration.ofSeconds(corteSegundos);
    }

    @Override
    public Optional<String> alEspanol(UUID actorId, String frase) {
        if (!claude.disponible()) {
            return Optional.empty();
        }
        String modelo = claude.modeloRapido();
        long inicio = System.nanoTime();
        ClaudeTexto.Respuesta respuesta;
        try {
            respuesta = claude.pedir(new ClaudeTexto.Pedido(modelo, OpenAiTraductorDeFrases.INSTRUCCIONES, frase,
                    400, OutputConfig.Effort.LOW, true, corte));
        } catch (ClaudeTexto.NoRespondio ex) {
            uso.texto(actorId, NAME, modelo, null, null, ms(inicio), AiUsageOutcome.TIMEOUT);
            return Optional.empty();
        } catch (RuntimeException ex) {
            uso.texto(actorId, NAME, modelo, null, null, ms(inicio), AiUsageOutcome.ERROR);
            log.warn("Claude falló al traducir una frase de Meissa: {}", ex.getMessage());
            return Optional.empty();
        }
        Optional<String> traducida = Optional.of(respuesta.texto()).map(String::strip).filter(t -> !t.isEmpty());
        uso.texto(actorId, NAME, modelo, respuesta.entrada(), respuesta.salida(), ms(inicio),
                traducida.isPresent() ? AiUsageOutcome.OK : AiUsageOutcome.INVALID_OUTPUT);
        return traducida.filter(t -> !OpenAiTraductorDeFrases.igual(t, frase));
    }

    private static int ms(long inicio) {
        return (int) ((System.nanoTime() - inicio) / 1_000_000);
    }
}
