package co.orion.teaching.application;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.models.messages.OutputConfig;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.shared.ai.ClaudeTexto;

/**
 * El borrador del acta con Claude Opus. Las reglas son las de {@link OpenAiLessonNoteDrafter}: el mismo
 * prompt, el mismo corte ({@code ai_note_timeout_seconds}), la misma validación del JSON, un reintento
 * y no más, y el tope de gasto consultado antes de llamar. Cualquier fallo devuelve vacío y el profesor
 * escribe a mano, sin un solo mensaje técnico.
 */
@Component
@ConditionalOnProperty(name = "orion.ai.text-provider", havingValue = "claude")
public class ClaudeLessonNoteDrafter implements LessonNoteDrafter {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLessonNoteDrafter.class);

    private final ClaudeTexto claude;
    private final TeachingAiBudget presupuesto;
    private final PlatformSettingsService settings;

    public ClaudeLessonNoteDrafter(ClaudeTexto claude, TeachingAiBudget presupuesto, PlatformSettingsService settings) {
        this.claude = claude;
        this.presupuesto = presupuesto;
        this.settings = settings;
    }

    @Override
    public Optional<Borrador> redactar(Contexto contexto) {
        if (!claude.disponible() || !presupuesto.disponible()) {
            return Optional.empty();
        }
        for (int intento = 1; intento <= 2; intento++) {
            Resultado r = llamar(contexto);
            if (r.borrador().isPresent() || !r.reintentable()) {
                return r.borrador();
            }
        }
        return Optional.empty();
    }

    private record Resultado(Optional<Borrador> borrador, boolean reintentable) {
    }

    private Resultado llamar(Contexto contexto) {
        String modelo = claude.modeloDeCalidad();
        Duration corte = Duration.ofSeconds(settings.getInt("ai_note_timeout_seconds"));
        long inicio = System.nanoTime();
        ClaudeTexto.Respuesta respuesta;
        try {
            respuesta = claude.pedir(new ClaudeTexto.Pedido(modelo,
                    OpenAiLessonNoteDrafter.instrucciones() + "\nResponde SOLO con el objeto JSON, sin texto antes ni después.",
                    OpenAiLessonNoteDrafter.entrada(contexto),
                    1800, OutputConfig.Effort.LOW, false, corte));
        } catch (ClaudeTexto.NoRespondio ex) {
            presupuesto.registrar(contexto.actorId(), modelo, null, null, ms(inicio), "TIMEOUT");
            return new Resultado(Optional.empty(), false);
        } catch (RuntimeException ex) {
            log.warn("Claude falló al redactar un acta: {}", ex.getMessage());
            presupuesto.registrar(contexto.actorId(), modelo, null, null, ms(inicio), "ERROR");
            return new Resultado(Optional.empty(), false);
        }
        Optional<Borrador> borrador = respuesta.rechazada()
                ? Optional.empty()
                : OpenAiLessonNoteDrafter.validar(soloElJson(respuesta.texto()));
        presupuesto.registrar(contexto.actorId(), modelo, respuesta.entrada(), respuesta.salida(), ms(inicio),
                borrador.isPresent() ? "OK" : "INVALID_OUTPUT");
        return new Resultado(borrador, borrador.isEmpty() && !respuesta.rechazada());
    }

    /** Si el modelo envolvió el JSON en una cerca de código o una frase, se queda solo el objeto. */
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
