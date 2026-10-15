package co.orion.practice.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.models.messages.OutputConfig;

import co.orion.shared.ai.ClaudeTexto;

/** «Tu frase», revisada por Claude Haiku: rápida, sin razonamiento. Mismo prompt y mismo veredicto JSON. */
@Component
@ConditionalOnProperty(name = "orion.ai.text-provider", havingValue = "claude")
public class ClaudeRevisorDeFrases implements RevisorDeFrases {

    private static final Logger log = LoggerFactory.getLogger(ClaudeRevisorDeFrases.class);
    private static final Duration CORTE = Duration.ofSeconds(8);

    private final ClaudeTexto claude;
    private final PracticeAiBudget presupuesto;

    public ClaudeRevisorDeFrases(ClaudeTexto claude, PracticeAiBudget presupuesto) {
        this.claude = claude;
        this.presupuesto = presupuesto;
    }

    @Override
    public Optional<Boolean> acepta(UUID estudianteId, String languageCode, String termino, String frase) {
        if (!claude.disponible() || !presupuesto.disponible()) {
            return Optional.empty();
        }
        String modelo = claude.modeloRapido();
        long inicio = System.nanoTime();
        ClaudeTexto.Respuesta respuesta;
        try {
            respuesta = claude.pedir(new ClaudeTexto.Pedido(modelo,
                    OpenAiRevisorDeFrases.instrucciones(languageCode) + "\nResponde SOLO con el JSON.",
                    "Término: «" + termino + "»\nFrase del estudiante: «" + frase + "»",
                    300, OutputConfig.Effort.LOW, true, CORTE));
        } catch (ClaudeTexto.NoRespondio ex) {
            presupuesto.registrar(estudianteId, modelo, null, null, ms(inicio), "TIMEOUT");
            return Optional.empty();
        } catch (RuntimeException ex) {
            presupuesto.registrar(estudianteId, modelo, null, null, ms(inicio), "ERROR");
            log.info("La revisión de «Tu frase» (Claude) no respondió; manda la regla: {}", ex.getMessage());
            return Optional.empty();
        }
        Optional<Boolean> veredicto = OpenAiRevisorDeFrases.leer(respuesta.texto());
        presupuesto.registrar(estudianteId, modelo, respuesta.entrada(), respuesta.salida(), ms(inicio),
                veredicto.isPresent() ? "OK" : "INVALID_OUTPUT");
        return veredicto;
    }

    private static int ms(long inicio) {
        return (int) ((System.nanoTime() - inicio) / 1_000_000);
    }
}
