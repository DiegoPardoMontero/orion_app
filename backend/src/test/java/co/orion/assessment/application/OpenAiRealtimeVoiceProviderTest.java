package co.orion.assessment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que se le pide a OpenAI al abrir la conversación, leído sin tocar la red.
 *
 * <p>Hasta el 22/09/2026 la sesión no pedía transcribir lo que dice la persona. OpenAI no lo hace
 * por su cuenta, así que el servidor nunca recibía un turno del usuario y todo diagnóstico se
 * cerraba sin número. Nada fallaba: la conversación sonaba bien y el resultado salía vacío.
 */
class OpenAiRealtimeVoiceProviderTest {

    private static final VoiceSessionRequest PEDIDO =
            new VoiceSessionRequest("EN", "guion", 120, "Ana");

    private static Map<?, ?> sesion(OpenAiRealtimeVoiceProvider provider) {
        return (Map<?, ?>) provider.cuerpo(PEDIDO).get("session");
    }

    private static OpenAiRealtimeVoiceProvider provider(String reasoningEffort) {
        return new OpenAiRealtimeVoiceProvider(
                "sk-test", "gpt-realtime-2.1-mini", "marin", reasoningEffort, "gpt-4o-mini-transcribe");
    }

    @Test
    @DisplayName("Pide transcribir lo que dice la persona, sin fijar el idioma")
    void pideLaTranscripcion() {
        Map<?, ?> audio = (Map<?, ?>) sesion(provider("minimal")).get("audio");
        Map<?, ?> transcripcion = (Map<?, ?>) ((Map<?, ?>) audio.get("input")).get("transcription");

        assertThat(transcripcion.get("model")).isEqualTo("gpt-4o-mini-transcribe");
        // Fijarlo en inglés traduciría una respuesta en español, que es la señal de la rama en español.
        assertThat(transcripcion.containsKey("language")).isFalse();
    }

    @Test
    @DisplayName("El razonamiento va al mínimo, y sin él no se manda el parámetro")
    void razonamientoAlMinimo() {
        assertThat(sesion(provider("minimal")).get("reasoning"))
                .isEqualTo(Map.of("effort", "minimal"));
        assertThat(sesion(provider("")).containsKey("reasoning")).isFalse();
    }

    @Test
    @DisplayName("El guion y la voz viajan como siempre")
    void guionYVoz() {
        Map<?, ?> sesion = sesion(provider("minimal"));
        Map<?, ?> audio = (Map<?, ?>) sesion.get("audio");

        assertThat(sesion.get("instructions")).isEqualTo("guion");
        assertThat(audio.get("output")).isEqualTo(Map.of("voice", "marin"));
    }
}
