package co.orion.teaching.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.teaching.domain.LessonNote;

/**
 * El acta, ordenada por un modelo de texto de OpenAI a partir de las notas del profesor.
 *
 * <p><strong>Lo que decide si esto sirve es la robustez</strong> (brief, paso A2): corte a los
 * {@code ai_note_timeout_seconds} (25), salida en JSON estricto validada contra el esquema —campos
 * de más, secciones de más de 1.200 caracteres, más de 12 palabras o una frase prohibida la
 * invalidan—, <strong>un reintento y no más</strong>, y el tope de gasto consultado antes de
 * llamar. Cualquier fallo devuelve vacío y el profesor escribe a mano en los mismos campos, sin un
 * solo mensaje de error técnico.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "openai")
public class OpenAiLessonNoteDrafter implements LessonNoteDrafter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLessonNoteDrafter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ENDPOINT = "https://api.openai.com/v1/chat/completions";
    static final String PROMPT = "prompts/lesson-note-v1.txt";
    static final String VERSION = "lesson-note-v1";

    private static final Set<String> CLAVES = Set.of("workedOn", "recurringIssues", "nextSteps", "vocabulary");
    private static final List<String> PROHIBIDAS = List.of("no sabes", "tu nivel es muy malo",
            "eso está completamente mal", "es muy fácil", "aprenderás inglés perfecto");

    private final String apiKey;
    private final String modelo;
    private final TeachingAiBudget presupuesto;
    private final PlatformSettingsService settings;

    public OpenAiLessonNoteDrafter(@Value("${OPENAI_API_KEY:}") String apiKey,
                                   @Value("${orion.teaching.model:gpt-5-mini}") String modelo,
                                   TeachingAiBudget presupuesto,
                                   PlatformSettingsService settings) {
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.presupuesto = presupuesto;
        this.settings = settings;
    }

    @Override
    public Optional<Borrador> redactar(Contexto contexto) {
        if (apiKey.isBlank() || !presupuesto.disponible()) {
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
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(4));
        fabrica.setReadTimeout(Duration.ofSeconds(settings.getInt("ai_note_timeout_seconds")));
        RestClient http = RestClient.builder().requestFactory(fabrica).build();

        long inicio = System.nanoTime();
        Map<?, ?> respuesta;
        try {
            respuesta = http.post().uri(ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo(contexto))
                    .retrieve()
                    .body(Map.class);
        } catch (ResourceAccessException ex) {
            presupuesto.registrar(contexto.actorId(), modelo, null, null, ms(inicio), "TIMEOUT");
            return new Resultado(Optional.empty(), false);
        } catch (RuntimeException ex) {
            log.warn("El proveedor falló al redactar un acta: {}", ex.getMessage());
            presupuesto.registrar(contexto.actorId(), modelo, null, null, ms(inicio), "ERROR");
            return new Resultado(Optional.empty(), false);
        }

        Integer entrada = tokens(respuesta, "prompt_tokens");
        Integer salida = tokens(respuesta, "completion_tokens");
        Optional<Borrador> borrador = contenido(respuesta).flatMap(OpenAiLessonNoteDrafter::validar);
        presupuesto.registrar(contexto.actorId(), modelo, entrada, salida, ms(inicio),
                borrador.isPresent() ? "OK" : "INVALID_OUTPUT");
        return new Resultado(borrador, borrador.isEmpty());
    }

    Map<String, Object> cuerpo(Contexto c) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", modelo);
        cuerpo.put("reasoning_effort", "minimal");
        cuerpo.put("max_completion_tokens", 1800);
        cuerpo.put("response_format", Map.of("type", "json_object"));
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", instrucciones()),
                Map.of("role", "user", "content", entrada(c))));
        return cuerpo;
    }

    /** D7: el proveedor ve nombre de pila, idioma, nivel y objetivo. Nada más del estudiante. */
    static String entrada(Contexto c) {
        return "Estudiante: " + (c.nombreDePila() == null ? "(sin nombre)" : c.nombreDePila())
                + "\nIdioma de la clase: " + (c.idioma() == null ? "inglés" : c.idioma())
                + "\nNivel que declara: " + (c.nivel() == null ? "no lo dijo" : c.nivel())
                + "\nObjetivo: " + (c.objetivo() == null ? "no lo dijo" : c.objetivo())
                + "\n\nNotas del profesor:\n" + c.notas();
    }

    /** JSON estricto con exactamente las cuatro claves y dentro de los límites; si no, vacío. */
    static Optional<Borrador> validar(String texto) {
        try {
            JsonNode raiz = JSON.readTree(texto);
            if (!raiz.isObject()) {
                return Optional.empty();
            }
            Iterator<String> nombres = raiz.fieldNames();
            while (nombres.hasNext()) {
                if (!CLAVES.contains(nombres.next())) {
                    return Optional.empty();
                }
            }
            String trabajado = seccion(raiz, "workedOn");
            String presente = seccion(raiz, "recurringIssues");
            String sigue = seccion(raiz, "nextSteps");
            if (trabajado == null || presente == null || sigue == null) {
                return Optional.empty();
            }
            List<Palabra> palabras = new ArrayList<>();
            JsonNode vocab = raiz.path("vocabulary");
            if (!vocab.isMissingNode() && !vocab.isNull()) {
                if (!vocab.isArray() || vocab.size() > 12) {
                    return Optional.empty();
                }
                for (JsonNode p : vocab) {
                    String termino = p.path("term").asText("").trim();
                    String significado = p.path("meaning").asText("").trim();
                    if (termino.isEmpty() || termino.length() > 120 || significado.length() > 300) {
                        return Optional.empty();
                    }
                    palabras.add(new Palabra(termino, significado.isEmpty() ? null : significado));
                }
            }
            String todo = (trabajado + " " + presente + " " + sigue).toLowerCase(Locale.forLanguageTag("es-CO"));
            if (PROHIBIDAS.stream().anyMatch(todo::contains)) {
                return Optional.empty();
            }
            return Optional.of(new Borrador(trabajado, presente, sigue, palabras, VERSION));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /** Una sección: texto (puede ir vacío) de hasta 1.200 caracteres; cualquier otra cosa invalida. */
    private static String seccion(JsonNode raiz, String clave) {
        JsonNode n = raiz.path(clave);
        if (n.isMissingNode() || n.isNull()) {
            return "";
        }
        if (!n.isTextual() || n.asText().length() > LessonNote.MAX_SECCION) {
            return null;
        }
        return n.asText().trim();
    }

    private static Optional<String> contenido(Map<?, ?> respuesta) {
        if (respuesta != null && respuesta.get("choices") instanceof List<?> opciones && !opciones.isEmpty()
                && opciones.getFirst() instanceof Map<?, ?> opcion
                && opcion.get("message") instanceof Map<?, ?> mensaje
                && mensaje.get("content") instanceof String contenido) {
            return Optional.of(contenido);
        }
        return Optional.empty();
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
