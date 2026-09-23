package co.orion.practice.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import co.orion.practice.domain.PracticeItemType;

/**
 * La práctica con {@code gpt-5-mini}, en JSON estricto. La salida no se cree: pasa por
 * {@link ValidadorDeEjercicios}, y lo que no se ancla al acta se descarta. Es texto y nada de voz:
 * la función más barata del bloque, y así se queda.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "openai")
public class OpenAiPracticeGenerator implements PracticeGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiPracticeGenerator.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    static final String PROMPT = "prompts/practice-v2.txt";

    private final RestClient http;
    private final String apiKey;
    private final String modelo;
    private final String endpoint;
    private final PracticeAiBudget presupuesto;

    public OpenAiPracticeGenerator(@Value("${OPENAI_API_KEY:}") String apiKey,
                                   @Value("${orion.practice.model:gpt-5-mini}") String modelo,
                                   @Value("${orion.practice.openai-endpoint:https://api.openai.com/v1/chat/completions}")
                                   String endpoint,
                                   @Value("${orion.practice.timeout-seconds:60}") int corteSegundos,
                                   PracticeAiBudget presupuesto) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(corteSegundos));
        this.http = RestClient.builder().requestFactory(fabrica).build();
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.endpoint = endpoint;
        this.presupuesto = presupuesto;
    }

    @Override
    public boolean disponible() {
        return !apiKey.isBlank() && presupuesto.disponible();
    }

    @Override
    public List<Generado> generar(UUID estudianteId, Material material, int cuantos) {
        long inicio = System.nanoTime();
        Map<?, ?> respuesta;
        try {
            respuesta = http.post().uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo(material, cuantos))
                    .retrieve()
                    .body(Map.class);
        } catch (ResourceAccessException ex) {
            presupuesto.registrar(estudianteId, modelo, null, null, ms(inicio), "TIMEOUT");
            throw new ProveedorNoRespondio("se agotó el tiempo");
        } catch (RuntimeException ex) {
            presupuesto.registrar(estudianteId, modelo, null, null, ms(inicio), "ERROR");
            log.warn("El proveedor falló al generar una práctica: {}", ex.getMessage());
            throw new ProveedorNoRespondio(ex.getMessage());
        }
        List<Generado> generados = leer(contenido(respuesta));
        presupuesto.registrar(estudianteId, modelo, tokens(respuesta, "prompt_tokens"),
                tokens(respuesta, "completion_tokens"), ms(inicio), generados.isEmpty() ? "INVALID_OUTPUT" : "OK");
        return generados;
    }

    Map<String, Object> cuerpo(Material material, int cuantos) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", modelo);
        cuerpo.put("reasoning_effort", "minimal");
        cuerpo.put("max_completion_tokens", 2500);
        cuerpo.put("response_format", Map.of("type", "json_object"));
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", instrucciones()),
                Map.of("role", "user", "content", entrada(material, cuantos))));
        return cuerpo;
    }

    /** Lo que ve el proveedor: el acta y el idioma. Nada del estudiante: ni nombre ni correo. */
    static String entrada(Material m, int cuantos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ejercicios que quiero: ").append(cuantos).append('\n');
        sb.append("Idioma de la clase: ").append(m.languageCode() == null ? "EN" : m.languageCode()).append('\n');
        sb.append("Lo que trabajaron: ").append(texto(m.workedOn())).append('\n');
        sb.append("Para tener presente (errores recurrentes): ").append(texto(m.recurringIssues())).append('\n');
        sb.append("Vocabulario:\n");
        for (Material.Termino t : m.vocabulary()) {
            sb.append("- ").append(t.term()).append(t.meaning() == null ? "" : " = " + t.meaning()).append('\n');
        }
        sb.append("Tipos para este set: ").append(String.join(", ",
                tiposPara(m, cuantos).stream().map(Enum::name).toList())).append('\n');
        return sb.toString();
    }

    /**
     * Qué tipos pedir: los que el acta alcanza a anclar y, si sobran, rotando cuál se queda fuera
     * según la clase. Dejándole la elección al modelo, elegía siempre los mismos cuatro y el diálogo
     * no salía nunca; así cada set trae una mezcla distinta y todos los tipos van apareciendo.
     */
    static List<PracticeItemType> tiposPara(Material m, int cuantos) {
        List<PracticeItemType> posibles = new ArrayList<>();
        if (!m.vocabulary().isEmpty()) {
            posibles.add(PracticeItemType.FILL_BLANK);
        }
        if (m.recurringIssues() != null && !m.recurringIssues().isBlank()) {
            posibles.add(PracticeItemType.FIX_SENTENCE);
        }
        if (m.vocabulary().size() >= 2) {
            posibles.add(PracticeItemType.MATCH_MEANING);
        }
        if (m.workedOn() != null && !m.workedOn().isBlank()) {
            posibles.add(PracticeItemType.ORDER_DIALOGUE);
        }
        if (!m.vocabulary().isEmpty()) {
            posibles.add(PracticeItemType.WRITE_SENTENCE);
        }
        if (posibles.size() <= cuantos) {
            return posibles;
        }
        Collections.rotate(posibles, -Math.floorMod(Objects.hashCode(m.bookingId()), posibles.size()));
        List<PracticeItemType> elegidos = new ArrayList<>(posibles.subList(0, cuantos));
        elegidos.sort(null);
        return elegidos;
    }

    static List<Generado> leer(String contenido) {
        List<Generado> salida = new ArrayList<>();
        if (contenido == null) {
            return salida;
        }
        try {
            for (JsonNode item : JSON.readTree(contenido).path("items")) {
                PracticeItemType tipo;
                try {
                    tipo = PracticeItemType.valueOf(item.path("type").asText(""));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                JsonNode esperado = item.path("expected");
                JsonNode payload = tipo == PracticeItemType.ORDER_DIALOGUE
                        ? desordenado(item.path("payload"), esperado) : item.path("payload");
                salida.add(new Generado(tipo, item.path("prompt").asText(null), payload.toString(),
                        esperado.isNull() || esperado.isMissingNode() ? null
                                : esperado.isTextual() ? esperado.asText() : esperado.toString(),
                        item.path("explanation").asText(null),
                        item.path("sourceTerm").isTextual() ? item.path("sourceTerm").asText() : null));
            }
        } catch (IOException ex) {
            return List.of();
        }
        return salida;
    }

    /**
     * A veces el modelo manda el diálogo ya en orden, y ordenar lo ordenado no es un ejercicio. En vez
     * de perderlo, se desordena aquí: primero las intervenciones impares y luego las pares
     * ({@code [1, 3, 0, 2]} para cuatro), que nunca coincide con el orden original.
     */
    static JsonNode desordenado(JsonNode payload, JsonNode esperado) {
        JsonNode lineas = payload.path("lines");
        if (!(payload instanceof ObjectNode objeto) || !lineas.isArray() || lineas.size() < 2 || !lineas.equals(esperado)) {
            return payload;
        }
        ArrayNode nuevas = JSON.createArrayNode();
        for (int inicio = 1; inicio >= 0; inicio--) {
            for (int i = inicio; i < lineas.size(); i += 2) {
                nuevas.add(lineas.get(i));
            }
        }
        ObjectNode copia = objeto.deepCopy();
        copia.set("lines", nuevas);
        return copia;
    }

    private static String texto(String s) {
        return s == null || s.isBlank() ? "(nada)" : s;
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
            throw new UncheckedIOException("No se pudo leer el prompt de práctica", ex);
        }
    }

    private static int ms(long inicio) {
        return (int) ((System.nanoTime() - inicio) / 1_000_000);
    }
}
