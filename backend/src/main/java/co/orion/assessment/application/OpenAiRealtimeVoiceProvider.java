package co.orion.assessment.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import co.orion.shared.error.ServiceUnavailableException;

/**
 * Conversación por voz contra la API en tiempo real de OpenAI.
 *
 * <p><strong>El audio no pasa por aquí.</strong> Este servicio pide una credencial efímera
 * ({@code POST /v1/realtime/client_secrets}) y se la entrega al navegador, que se conecta directo
 * por WebRTC. La llave de la cuenta nunca sale del servidor y los minutos de audio no tocan
 * nuestra infraestructura — que es exactamente lo que se quiere con un dato sensible.
 *
 * <p>El modelo va por configuración y no como constante: cambiar de {@code mini} a uno mayor
 * multiplica la factura por tres, y esa es una decisión que alguien tiene que poder tomar sin un
 * despliegue.
 *
 * <p>Las <strong>instrucciones</strong> del guion se mandan al abrir la sesión. No se envían desde
 * el navegador: si el guion viajara al cliente, cualquiera podría reemplazarlo — y con él, las
 * prohibiciones de nunca corregir y nunca evaluar en voz alta, que son las que hacen que la
 * medición valga algo.
 */
@Component
@ConditionalOnProperty(name = "orion.assessment.voice.provider", havingValue = "openai")
public class OpenAiRealtimeVoiceProvider implements VoiceConversationProvider {

    static final String NAME = "openai-realtime";
    private static final String ENDPOINT = "https://api.openai.com/v1/realtime/client_secrets";

    private final RestClient http = RestClient.create();
    private final String apiKey;
    private final String model;
    private final String voice;

    public OpenAiRealtimeVoiceProvider(
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${orion.assessment.voice.model:gpt-realtime-2.1-mini}") String model,
            @Value("${orion.assessment.voice.voice:marin}") String voice) {
        this.apiKey = apiKey;
        this.model = model;
        this.voice = voice;
    }

    @Override
    public VoiceSession start(VoiceSessionRequest request) {
        if (apiKey.isBlank()) {
            throw new ServiceUnavailableException(
                    "El diagnóstico no está disponible en este momento. Inténtalo más tarde.");
        }

        Map<String, Object> cuerpo = Map.of(
                // La credencial vive lo que dure la conversación y un minuto más para conectarse.
                "expires_after", Map.of("anchor", "created_at", "seconds", request.maxSeconds() + 60),
                "session", Map.of(
                        "type", "realtime",
                        "model", model,
                        "instructions", request.scenarioPrompt(),
                        "output_modalities", List.of("audio"),
                        "audio", Map.of("output", Map.of("voice", voice))));

        Map<?, ?> respuesta;
        try {
            respuesta = http.post()
                    .uri(ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException ex) {
            // Se traduce a 503 y no se deja subir: quien está al otro lado no tiene por qué leer
            // el error de un tercero, y el registro de consumo lo anota el servicio que llama.
            throw new ServiceUnavailableException(
                    "No pudimos abrir la conversación. Podemos retomar cuando quieras.");
        }

        if (respuesta == null || respuesta.get("value") == null) {
            throw new ServiceUnavailableException(
                    "No pudimos abrir la conversación. Podemos retomar cuando quieras.");
        }

        Object sesion = respuesta.get("session");
        String ref = sesion instanceof Map<?, ?> m && m.get("id") != null
                ? m.get("id").toString()
                : respuesta.get("value").toString();

        return new VoiceSession(
                ref,
                respuesta.get("value").toString(),
                expiracion(respuesta.get("expires_at")),
                model);
    }

    /** {@code expires_at} llega en segundos desde la época, no en milisegundos. */
    private static Instant expiracion(Object expiresAt) {
        if (expiresAt instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue());
        }
        return Instant.now();
    }

    @Override
    public void stop(String sessionRef) {
        // La credencial efímera caduca sola y la sesión muere con la conexión del navegador. No hay
        // nada que cerrar desde el servidor, y fingir que lo hay sería una llamada de red inútil en
        // el camino de cierre del diagnóstico.
    }

    @Override
    public String name() {
        return NAME;
    }
}
