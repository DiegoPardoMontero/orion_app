package co.orion.assessment.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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

import co.orion.assessment.domain.AiUsageOutcome;

/**
 * El resumen personalizado, escrito por un modelo de texto de OpenAI a partir de la transcripción.
 *
 * <p>Se enciende con el mismo interruptor que la voz ({@code orion.assessment.voice.provider}):
 * quien tiene la conversación de verdad tiene también el resumen de verdad, y no hay una segunda
 * variable de Railway que olvidar.
 *
 * <p><strong>Rápido o nada.</strong> La persona lo espera en la pantalla de «estamos leyendo cómo
 * hablaste», así que la llamada tiene un corte de doce segundos y el razonamiento va al mínimo. Si
 * no llega a tiempo, el resultado sale igual con la frase de plantilla.
 *
 * <p><strong>La salida se revisa antes de mostrarse.</strong> Si trae una palabra prohibida del
 * diagnóstico o se pasa de largo, se descarta: una frase genérica es mejor que una que evalúa a la
 * persona con nuestro membrete. Un solo intento; reintentar aquí es hacer esperar más a alguien que
 * ya está esperando.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "openai")
public class OpenAiConversationSummarizer implements ConversationSummarizer {

    private static final Logger log = LoggerFactory.getLogger(OpenAiConversationSummarizer.class);

    static final String NAME = "openai-text";
    static final String PROMPT = "prompts/assessment-summary-v1.txt";
    static final int LARGO_MAXIMO = 480;

    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";

    /**
     * Lo que el resumen no puede decir, ni siquiera negado: las familias de la regla de marca del
     * diagnóstico (elogio, examen, nivel). La lista completa y su porqué están en
     * {@code TextosDelDiagnosticoTest}; esta es la red en tiempo de ejecución para un texto que no
     * escribimos nosotros.
     */
    private static final Pattern PROHIBIDO = Pattern.compile(
            "\\b(examen|prueba|test|nota|calificaci[oó]n|evaluaci[oó]n|nivel|mcer|"
                    + "a1|a2|b1|b2|c1|c2|excelente|muy bien|perfecto|perfecta)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final RestClient http;
    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final AiUsageRecorder uso;

    public OpenAiConversationSummarizer(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${orion.assessment.summary.model:gpt-5-mini}") String model,
            @Value("${orion.assessment.summary.reasoning-effort:minimal}") String reasoningEffort,
            AiUsageRecorder uso) {
        // El cliente HTTP del JDK: con HttpURLConnection el corte de lectura no cortaba un POST lento
        // (lo mostró la prueba del acta contra un servidor que tarda).
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(12));
        this.http = RestClient.builder().requestFactory(fabrica).build();
        this.apiKey = apiKey;
        this.model = model;
        this.reasoningEffort = reasoningEffort;
        this.uso = uso;
    }

    @Override
    public Optional<String> resumir(Pedido pedido) {
        if (apiKey.isBlank()) {
            return Optional.empty();
        }
        long inicio = System.nanoTime();
        Map<?, ?> respuesta;
        try {
            respuesta = http.post()
                    .uri(ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo(pedido))
                    .retrieve()
                    .body(Map.class);
        } catch (ResourceAccessException ex) {
            uso.texto(pedido.actorId(), NAME, model, null, null, ms(inicio), AiUsageOutcome.TIMEOUT);
            log.warn("El resumen del diagnóstico no llegó a tiempo: {}", ex.getMessage());
            return Optional.empty();
        } catch (RuntimeException ex) {
            uso.texto(pedido.actorId(), NAME, model, null, null, ms(inicio), AiUsageOutcome.ERROR);
            log.warn("El proveedor falló al resumir el diagnóstico: {}", ex.getMessage());
            return Optional.empty();
        }

        Integer entrada = tokens(respuesta, "prompt_tokens");
        Integer salida = tokens(respuesta, "completion_tokens");
        Optional<String> texto = texto(respuesta).map(OpenAiConversationSummarizer::limpiar)
                .filter(OpenAiConversationSummarizer::aceptable);
        uso.texto(pedido.actorId(), NAME, model, entrada, salida, ms(inicio),
                texto.isPresent() ? AiUsageOutcome.OK : AiUsageOutcome.INVALID_OUTPUT);
        return texto;
    }

    Map<String, Object> cuerpo(Pedido pedido) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", model);
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", instrucciones()),
                Map.of("role", "user", "content", conversacion(pedido))));
        // Tope de salida: el razonamiento también cuenta aquí, y un resumen de tres frases no
        // necesita más. Sin él, una respuesta desbocada se pagaría entera.
        cuerpo.put("max_completion_tokens", 600);
        if (!reasoningEffort.isBlank()) {
            cuerpo.put("reasoning_effort", reasoningEffort);
        }
        return cuerpo;
    }

    /** Lo que ve el proveedor: nombre de pila, objetivos y la conversación. Nada más. */
    static String conversacion(Pedido pedido) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nombre de pila: ").append(pedido.nombreDePila() == null || pedido.nombreDePila().isBlank()
                ? "(no lo sabemos)" : pedido.nombreDePila()).append('\n');
        sb.append("Objetivos que marcó antes de empezar: ")
                .append(pedido.objetivos().isEmpty() ? "ninguno" : String.join(", ", pedido.objetivos()))
                .append("\n\nConversación (M = Meissa, P = la persona):\n");
        for (Turno turno : pedido.turnos()) {
            if (turno.texto() == null || turno.texto().isBlank()) {
                continue;
            }
            sb.append(turno.deMeissa() ? "M: " : "P: ").append(turno.texto().trim()).append('\n');
        }
        return sb.toString();
    }

    static boolean aceptable(String texto) {
        return !texto.isBlank()
                && texto.length() <= LARGO_MAXIMO
                && !PROHIBIDO.matcher(texto.toLowerCase(Locale.forLanguageTag("es-CO"))).find();
    }

    /** Sin comillas envolventes ni saltos de línea: es un párrafo que va dentro de una tarjeta. */
    static String limpiar(String texto) {
        String limpio = texto.trim().replaceAll("\\s*\\n+\\s*", " ");
        if (limpio.length() >= 2 && "\"«“".indexOf(limpio.charAt(0)) >= 0
                && "\"»”".indexOf(limpio.charAt(limpio.length() - 1)) >= 0) {
            limpio = limpio.substring(1, limpio.length() - 1).trim();
        }
        return limpio;
    }

    private static Optional<String> texto(Map<?, ?> respuesta) {
        if (respuesta == null || !(respuesta.get("choices") instanceof List<?> opciones)
                || opciones.isEmpty() || !(opciones.getFirst() instanceof Map<?, ?> opcion)
                || !(opcion.get("message") instanceof Map<?, ?> mensaje)
                || !(mensaje.get("content") instanceof String contenido)) {
            return Optional.empty();
        }
        return Optional.of(contenido);
    }

    private static Integer tokens(Map<?, ?> respuesta, String campo) {
        if (respuesta != null && respuesta.get("usage") instanceof Map<?, ?> usage
                && usage.get(campo) instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static String instrucciones() {
        try {
            String archivo = new String(new ClassPathResource(PROMPT).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            return archivo.lines().filter(l -> !l.startsWith("#"))
                    .reduce(new StringBuilder(), (sb, l) -> sb.append(l).append('\n'), (a, b) -> a)
                    .toString().trim();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer " + PROMPT, ex);
        }
    }

    private static int ms(long inicio) {
        return (int) ((System.nanoTime() - inicio) / 1_000_000);
    }
}
