package co.orion.shared.ai;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.RequestOptions;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.anthropic.models.messages.ThinkingConfigParam;

/**
 * El texto con IA de Orión pasa por Claude (decisión de Pardo, 14/10/2026): el acta, la práctica, el
 * resumen del diagnóstico, la traducción en vivo de Meissa y los revisores. La voz de Meissa sigue en
 * OpenAI Realtime, que es lo único que Claude no hace.
 *
 * <p>Dos modelos: {@code claude-opus-5-5} para lo que se lee (acta, ejercicios, resumen) y
 * {@code claude-haiku-5-5} para lo que tiene que volver en segundos (traducir una frase mientras
 * Meissa la dice, revisar una respuesta). Cada función fija su corte de tiempo y su esfuerzo; aquí
 * solo vive el cliente y la forma de pedir.
 *
 * <p>Sin reintentos del SDK a propósito: cada función ya decide si reintenta (el acta, una vez) o si
 * descarta (la traducción tardía no sirve). Un reintento escondido duplicaría el gasto y reventaría
 * los cortes de cinco segundos.
 */
@Component
public class ClaudeTexto {

    private static final Logger log = LoggerFactory.getLogger(ClaudeTexto.class);

    /** El proveedor que se escribe en la tabla de uso de IA. */
    public static final String PROVEEDOR = "claude";

    private final AnthropicClient client;
    private final String modeloDeCalidad;
    private final String modeloRapido;

    public ClaudeTexto(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
                       // Solo para que las pruebas apunten a un servidor local que tarda, falla o responde mal.
                       @Value("${orion.ai.claude.base-url:}") String baseUrl,
                       @Value("${orion.ai.claude.quality-model:claude-opus-5-5}") String modeloDeCalidad,
                       @Value("${orion.ai.claude.fast-model:claude-haiku-5-5}") String modeloRapido) {
        this.modeloDeCalidad = modeloDeCalidad;
        this.modeloRapido = modeloRapido;
        if (apiKey == null || apiKey.isBlank()) {
            this.client = null;
            return;
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(apiKey.strip())
                .maxRetries(0)
                .timeout(Duration.ofSeconds(60));
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        this.client = builder.build();
    }

    public boolean disponible() {
        return client != null;
    }

    public String modeloDeCalidad() {
        return modeloDeCalidad;
    }

    public String modeloRapido() {
        return modeloRapido;
    }

    /**
     * @param esfuerzo    cuánto piensa el modelo; nulo deja el valor por defecto del modelo
     * @param sinPensar   apaga el razonamiento (solo lo admite el modelo rápido, a esfuerzo bajo):
     *                    para una traducción de una frase, pensar es tardar
     */
    public record Pedido(String modelo, String sistema, String usuario, long maxTokens,
                         OutputConfig.Effort esfuerzo, boolean sinPensar, Duration corte) {
    }

    /** Lo que volvió: el texto (todos los bloques de texto, seguidos), los tokens y por qué paró. */
    public record Respuesta(String texto, Integer entrada, Integer salida, String parada) {

        public boolean rechazada() {
            return "refusal".equals(parada);
        }
    }

    /** El proveedor no respondió a tiempo o falló por lo suyo (red, 429, 5xx): repetir puede servir. */
    public static final class NoRespondio extends RuntimeException {
        public NoRespondio(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    /** El proveedor rechazó la petición por lo que se le mandó (4xx): repetirla daría lo mismo. */
    public static final class Fallo extends RuntimeException {
        public Fallo(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    public Respuesta pedir(Pedido pedido) {
        if (client == null) {
            throw new IllegalStateException("Claude no está configurado (ANTHROPIC_API_KEY)");
        }
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(pedido.modelo())
                .maxTokens(pedido.maxTokens())
                .addUserMessage(pedido.usuario());
        if (pedido.sistema() != null && !pedido.sistema().isBlank()) {
            params.system(pedido.sistema());
        }
        if (pedido.esfuerzo() != null) {
            params.outputConfig(OutputConfig.builder().effort(pedido.esfuerzo()).build());
        }
        if (pedido.sinPensar()) {
            params.thinking(ThinkingConfigParam.ofDisabled(ThinkingConfigDisabled.builder().build()));
        }
        Message mensaje;
        try {
            mensaje = client.messages().create(params.build(),
                    RequestOptions.builder().timeout(pedido.corte()).build());
        } catch (AnthropicIoException | AnthropicRetryableException ex) {
            throw new NoRespondio(ex.getMessage(), ex);
        } catch (AnthropicServiceException ex) {
            log.warn("Claude respondió {} a {}: {}", ex.statusCode(), pedido.modelo(), ex.getMessage());
            throw new Fallo(ex.getMessage(), ex);
        }
        StringBuilder texto = new StringBuilder();
        mensaje.content().forEach(bloque -> bloque.text().ifPresent(t -> texto.append(t.text())));
        Optional<StopReason> parada = mensaje.stopReason();
        return new Respuesta(texto.toString(),
                (int) mensaje.usage().inputTokens(), (int) mensaje.usage().outputTokens(),
                parada.map(StopReason::asString).orElse(null));
    }
}
