package co.orion.assessment.application;

import java.net.http.HttpClient;
import java.text.Normalizer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import co.orion.assessment.domain.AiUsageOutcome;

/**
 * La traducción con un modelo de texto pequeño de OpenAI, la misma llave del diagnóstico.
 *
 * <p><strong>Rápida o nada.</strong> La frase tiene que aparecer mientras Meissa todavía la está
 * diciendo, así que el corte es de cinco segundos. El modelo es {@code gpt-4.1-nano}, sin
 * razonamiento: medido el 22/09/2026, ~1 s por frase, contra 1–3 s de {@code gpt-5-nano} con
 * razonamiento mínimo. Una traducción que llega tarde ya no sirve: se descarta sin reintentar.
 *
 * <p>Cuesta del orden de un peso por diagnóstico, y se carga al mismo presupuesto que la voz: es
 * parte de la misma función, y el tope es uno por función.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "openai")
public class OpenAiTraductorDeFrases implements TraductorDeFrases {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTraductorDeFrases.class);

    static final String NAME = "openai-traduccion";

    static final String INSTRUCCIONES = """
            Traduces al español de Colombia lo que dice Meissa, la voz de una academia de idiomas, \
            a una persona que practica inglés. Traducción natural, cálida y fiel: ni más larga ni \
            más formal que el original. Los nombres Orión y Meissa no se traducen. Devuelve SOLO la \
            traducción, sin comillas, sin notas y sin explicar nada. Si la frase ya está en \
            español, devuelve exactamente la misma frase.""";

    private final RestClient http;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final String endpoint;
    private final AiUsageRecorder uso;

    public OpenAiTraductorDeFrases(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${orion.assessment.translation.model:gpt-4.1-nano}") String model,
            @Value("${orion.assessment.translation.reasoning-effort:}") String reasoningEffort,
            @Value("${orion.assessment.translation.endpoint:https://api.openai.com/v1/chat/completions}") String endpoint,
            @Value("${orion.assessment.translation.timeout-seconds:5}") int corteSegundos,
            AiUsageRecorder uso) {
        // El cliente HTTP del JDK: con HttpURLConnection el corte de lectura no cortaba un POST lento
        // (lo mostró la prueba del acta contra un servidor que tarda).
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(corteSegundos));
        this.http = RestClient.builder().requestFactory(fabrica).build();
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.endpoint = endpoint;
        this.uso = uso;
    }

    @Override
    public Optional<String> alEspanol(UUID actorId, String frase) {
        if (apiKey.isBlank()) {
            return Optional.empty();
        }
        long inicio = System.nanoTime();
        Map<?, ?> respuesta;
        try {
            respuesta = http.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo(frase))
                    .retrieve()
                    .body(Map.class);
        } catch (ResourceAccessException ex) {
            uso.texto(actorId, NAME, model, null, null, ms(inicio), AiUsageOutcome.TIMEOUT);
            return Optional.empty();
        } catch (RuntimeException ex) {
            uso.texto(actorId, NAME, model, null, null, ms(inicio), AiUsageOutcome.ERROR);
            log.warn("El proveedor falló al traducir una frase de Meissa: {}", ex.getMessage());
            return Optional.empty();
        }

        Optional<String> traducida = texto(respuesta).map(String::strip).filter(t -> !t.isEmpty());
        uso.texto(actorId, NAME, model, tokens(respuesta, "prompt_tokens"),
                tokens(respuesta, "completion_tokens"), ms(inicio),
                traducida.isPresent() ? AiUsageOutcome.OK : AiUsageOutcome.INVALID_OUTPUT);
        return traducida.filter(t -> !igual(t, frase));
    }

    Map<String, Object> cuerpo(String frase) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", model);
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", INSTRUCCIONES),
                Map.of("role", "user", "content", frase)));
        // Una frase traducida no necesita más; el razonamiento también cuenta en este tope.
        cuerpo.put("max_completion_tokens", 400);
        if (!reasoningEffort.isBlank()) {
            cuerpo.put("reasoning_effort", reasoningEffort);
        }
        return cuerpo;
    }

    /** La rama en español: si vuelve lo mismo, no hay nada que mostrar debajo. */
    static boolean igual(String a, String b) {
        return normalizar(a).equals(normalizar(b));
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").strip().toLowerCase();
    }

    private static Optional<String> texto(Map<?, ?> respuesta) {
        if (respuesta != null && respuesta.get("choices") instanceof List<?> opciones && !opciones.isEmpty()
                && opciones.getFirst() instanceof Map<?, ?> opcion
                && opcion.get("message") instanceof Map<?, ?> mensaje
                && mensaje.get("content") instanceof String contenido) {
            return Optional.of(contenido);
        }
        return Optional.empty();
    }

    private static Integer tokens(Map<?, ?> respuesta, String campo) {
        if (respuesta != null && respuesta.get("usage") instanceof Map<?, ?> uso
                && uso.get(campo) instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static int ms(long inicio) {
        return (int) ((System.nanoTime() - inicio) / 1_000_000);
    }
}
