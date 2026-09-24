package co.orion.practice.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * «Tu frase», revisada por {@code gpt-5-mini}: si es inglés con sentido y usa el término. Es la
 * única evaluación de la práctica que pasa por la IA mientras el estudiante espera, así que es
 * corta, con tope de tiempo y, si algo falla, no decide nada: manda la regla de siempre.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "openai")
public class OpenAiRevisorDeFrases implements RevisorDeFrases {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRevisorDeFrases.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String PROMPT = "prompts/practice-sentence-check-v1.txt";

    private final RestClient http;
    private final String apiKey;
    private final String modelo;
    private final String endpoint;
    private final PracticeAiBudget presupuesto;

    public OpenAiRevisorDeFrases(@Value("${OPENAI_API_KEY:}") String apiKey,
                                 @Value("${orion.practice.model:gpt-5-mini}") String modelo,
                                 @Value("${orion.practice.openai-endpoint:https://api.openai.com/v1/chat/completions}")
                                 String endpoint,
                                 @Value("${orion.practice.sentence-check-timeout-seconds:8}") int corteSegundos,
                                 PracticeAiBudget presupuesto) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(corteSegundos));
        this.http = RestClient.builder().requestFactory(fabrica).build();
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.endpoint = endpoint;
        this.presupuesto = presupuesto;
    }

    @Override
    public Optional<Boolean> acepta(UUID estudianteId, String termino, String frase) {
        if (apiKey.isBlank() || !presupuesto.disponible()) {
            return Optional.empty();
        }
        long inicio = System.nanoTime();
        Map<?, ?> respuesta;
        try {
            respuesta = http.post().uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo(termino, frase))
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException ex) {
            presupuesto.registrar(estudianteId, modelo, null, null, ms(inicio),
                    ex instanceof ResourceAccessException ? "TIMEOUT" : "ERROR");
            log.info("La revisión de «Tu frase» no respondió; manda la regla: {}", ex.getMessage());
            return Optional.empty();
        }
        Optional<Boolean> veredicto = leer(contenido(respuesta));
        presupuesto.registrar(estudianteId, modelo, tokens(respuesta, "prompt_tokens"),
                tokens(respuesta, "completion_tokens"), ms(inicio), veredicto.isPresent() ? "OK" : "INVALID_OUTPUT");
        return veredicto;
    }

    Map<String, Object> cuerpo(String termino, String frase) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", modelo);
        cuerpo.put("reasoning_effort", "minimal");
        cuerpo.put("max_completion_tokens", 300);
        cuerpo.put("response_format", Map.of("type", "json_object"));
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", instrucciones()),
                Map.of("role", "user", "content", "Término: «" + termino + "»\nFrase del estudiante: «" + frase + "»")));
        return cuerpo;
    }

    static Optional<Boolean> leer(String contenido) {
        if (contenido == null) {
            return Optional.empty();
        }
        try {
            JsonNode ok = JSON.readTree(contenido).path("ok");
            return ok.isBoolean() ? Optional.of(ok.asBoolean()) : Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private static String contenido(Map<?, ?> respuesta) {
        if (respuesta != null && respuesta.get("choices") instanceof List<?> opciones && !opciones.isEmpty()
                && opciones.getFirst() instanceof Map<?, ?> opcion
                && opcion.get("message") instanceof Map<?, ?> mensaje
                && mensaje.get("content") instanceof String c) {
            return c;
        }
        return null;
    }

    private static Integer tokens(Map<?, ?> respuesta, String campo) {
        if (respuesta != null && respuesta.get("usage") instanceof Map<?, ?> uso && uso.get(campo) instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static String instrucciones() {
        try {
            String texto = new String(new ClassPathResource(PROMPT).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return texto.lines().filter(l -> !l.startsWith("#")).reduce("", (a, b) -> a + b + "\n").trim();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer la revisión de frases", ex);
        }
    }

    private static int ms(long inicio) {
        return (int) ((System.nanoTime() - inicio) / 1_000_000);
    }
}
