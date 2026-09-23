package co.orion.teaching.application;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * El dictado con el modelo de transcripción de OpenAI, la misma llave de todo lo demás.
 *
 * <p>El audio no se guarda en ninguna parte: llega, se reenvía, vuelve el texto y se descarta. Se
 * cobra por minuto (~10 pesos el minuto) y va al presupuesto del acta, que es de la misma función.
 * La pista {@code prompt} le dice al modelo que espera español con términos en inglés: sin ella,
 * «used to» sale transcrito como «iust tu».
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "openai")
public class OpenAiTranscriptorDeDictado implements TranscriptorDeDictado {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTranscriptorDeDictado.class);

    static final String PISTA = "Notas de un profesor de idiomas sobre su clase, en español, con palabras "
            + "y frases en inglés: past simple, used to, phrasal verbs.";

    private final RestClient http;
    private final String apiKey;
    private final String modelo;
    private final String endpoint;
    private final double dolaresPorMinuto;
    private final long pesosPorDolar;
    private final TeachingAiBudget presupuesto;

    public OpenAiTranscriptorDeDictado(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${orion.teaching.dictation.model:gpt-4o-mini-transcribe}") String modelo,
            @Value("${orion.teaching.dictation.endpoint:https://api.openai.com/v1/audio/transcriptions}") String endpoint,
            @Value("${orion.teaching.dictation.usd-per-minute:0.003}") double dolaresPorMinuto,
            @Value("${orion.ai.usd-to-cop:3101}") long pesosPorDolar,
            TeachingAiBudget presupuesto) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(40));
        this.http = RestClient.builder().requestFactory(fabrica).build();
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.endpoint = endpoint;
        this.dolaresPorMinuto = dolaresPorMinuto;
        this.pesosPorDolar = pesosPorDolar;
        this.presupuesto = presupuesto;
    }

    @Override
    public Optional<String> transcribir(UUID profesorId, byte[] audio, String tipo, int segundos) {
        if (apiKey.isBlank()) {
            return Optional.empty();
        }
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return tipo.contains("mp4") ? "dictado.mp4" : tipo.contains("ogg") ? "dictado.ogg" : "dictado.webm";
            }
        });
        partes.add("model", modelo);
        partes.add("prompt", PISTA);
        partes.add("response_format", "json");

        long pesos = (long) Math.ceil(segundos / 60.0 * dolaresPorMinuto * pesosPorDolar);
        long inicio = System.nanoTime();
        try {
            Map<?, ?> respuesta = http.post().uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(partes)
                    .retrieve()
                    .body(Map.class);
            Optional<String> texto = Optional.ofNullable(respuesta)
                    .map(r -> r.get("text")).filter(String.class::isInstance).map(String.class::cast)
                    .map(String::strip).filter(t -> !t.isEmpty());
            presupuesto.registrarAudio(profesorId, modelo, segundos, pesos, ms(inicio),
                    texto.isPresent() ? "OK" : "INVALID_OUTPUT");
            return texto;
        } catch (ResourceAccessException ex) {
            presupuesto.registrarAudio(profesorId, modelo, segundos, pesos, ms(inicio), "TIMEOUT");
            return Optional.empty();
        } catch (RuntimeException ex) {
            presupuesto.registrarAudio(profesorId, modelo, segundos, pesos, ms(inicio), "ERROR");
            log.warn("El proveedor falló al transcribir un dictado: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static int ms(long inicio) {
        return (int) ((System.nanoTime() - inicio) / 1_000_000);
    }
}
