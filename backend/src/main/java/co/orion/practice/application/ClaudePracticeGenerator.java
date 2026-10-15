package co.orion.practice.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.JsonNode;

import co.orion.shared.ai.ClaudeTexto;
import co.orion.shared.text.NombreDelIdioma;

/**
 * La práctica generada por Claude: Opus para inventar los ejercicios y Haiku para revisarlos (resolverlos
 * como si fuera el estudiante y descartar los ambiguos). Prompts, entrada, lectura del JSON y revisión
 * son los de {@link OpenAiPracticeGenerator}: solo cambia quién responde. Corre en segundo plano, así
 * que puede pensar un poco más que el acta.
 */
@Component
@ConditionalOnProperty(name = "orion.ai.text-provider", havingValue = "claude")
public class ClaudePracticeGenerator implements PracticeGenerator {

    private static final Logger log = LoggerFactory.getLogger(ClaudePracticeGenerator.class);
    private static final Duration CORTE = Duration.ofSeconds(90);
    private static final Duration CORTE_REVISION = Duration.ofSeconds(45);

    private final ClaudeTexto claude;
    private final PracticeAiBudget presupuesto;

    public ClaudePracticeGenerator(ClaudeTexto claude, PracticeAiBudget presupuesto) {
        this.claude = claude;
        this.presupuesto = presupuesto;
    }

    @Override
    public boolean disponible() {
        return claude.disponible() && presupuesto.disponible();
    }

    @Override
    public List<Generado> generar(UUID estudianteId, Material material, int cuantos) {
        String modelo = claude.modeloDeCalidad();
        long inicio = System.nanoTime();
        ClaudeTexto.Respuesta respuesta;
        try {
            respuesta = claude.pedir(new ClaudeTexto.Pedido(modelo,
                    OpenAiPracticeGenerator.instrucciones() + "\nResponde SOLO con el objeto JSON, sin texto antes ni después.",
                    OpenAiPracticeGenerator.entrada(material, cuantos),
                    6000, OutputConfig.Effort.MEDIUM, false, CORTE));
        } catch (ClaudeTexto.NoRespondio ex) {
            presupuesto.registrar(estudianteId, modelo, null, null, ms(inicio), "TIMEOUT");
            throw new ProveedorNoRespondio("Claude no respondió: " + ex.getMessage());
        } catch (RuntimeException ex) {
            // Un 4xx es por lo que se le mandó, y repetirlo daría lo mismo: el set gasta su intento.
            presupuesto.registrar(estudianteId, modelo, null, null, ms(inicio), "ERROR");
            log.warn("Claude no pudo generar una práctica: {}", ex.getMessage());
            return List.of();
        }
        List<Generado> generados = respuesta.rechazada() ? List.of()
                : OpenAiPracticeGenerator.leer(soloElJson(respuesta.texto()));
        presupuesto.registrar(estudianteId, modelo, respuesta.entrada(), respuesta.salida(), ms(inicio),
                generados.isEmpty() ? "INVALID_OUTPUT" : "OK");
        return revisados(estudianteId, material.languageCode(), generados);
    }

    /** La revisión con el modelo rápido; si falla, los ejercicios siguen sin revisar (mejora, no puerta). */
    List<Generado> revisados(UUID estudianteId, String languageCode, List<Generado> generados) {
        String ejercicios = OpenAiPracticeGenerator.ejerciciosParaRevisar(generados);
        if (ejercicios == null) {
            return generados;
        }
        String modelo = claude.modeloRapido();
        long inicio = System.nanoTime();
        ClaudeTexto.Respuesta respuesta;
        try {
            respuesta = claude.pedir(new ClaudeTexto.Pedido(modelo,
                    OpenAiPracticeGenerator.leerPrompt(OpenAiPracticeGenerator.REVISION)
                            .replace("{{IDIOMA}}", NombreDelIdioma.enEspanol(languageCode))
                            + "\nResponde SOLO con el objeto JSON.",
                    ejercicios, 2500, OutputConfig.Effort.LOW, false, CORTE_REVISION));
        } catch (RuntimeException ex) {
            presupuesto.registrar(estudianteId, modelo, null, null, ms(inicio),
                    ex instanceof ClaudeTexto.NoRespondio ? "TIMEOUT" : "ERROR");
            log.info("La revisión de la práctica (Claude) no respondió; los ejercicios quedan sin revisar: {}",
                    ex.getMessage());
            return generados;
        }
        Map<Integer, JsonNode> respuestas = OpenAiPracticeGenerator.respuestasDe(soloElJson(respuesta.texto()));
        presupuesto.registrar(estudianteId, modelo, respuesta.entrada(), respuesta.salida(), ms(inicio),
                respuestas.isEmpty() ? "INVALID_OUTPUT" : "OK");
        return OpenAiPracticeGenerator.aplicarRevision(generados, respuestas);
    }

    static String soloElJson(String texto) {
        if (texto == null) {
            return "";
        }
        int inicio = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        return inicio >= 0 && fin > inicio ? texto.substring(inicio, fin + 1) : texto;
    }

    private static int ms(long inicio) {
        return (int) ((System.nanoTime() - inicio) / 1_000_000);
    }
}
