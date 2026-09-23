package co.orion.teaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.support.ServidorDePrueba;

/** El dictado contra un proveedor de mentira: lo que se le manda, lo que se cobra y cómo falla. */
class OpenAiTranscriptorDeDictadoTest {

    private final ServidorDePrueba proveedor = new ServidorDePrueba();
    private final TeachingAiBudget presupuesto = mock(TeachingAiBudget.class);
    private final OpenAiTranscriptorDeDictado transcriptor = new OpenAiTranscriptorDeDictado("sk-test",
            "gpt-4o-mini-transcribe", proveedor.url("/v1/audio/transcriptions"), 0.003, 3101, 1, presupuesto);
    private final UUID maria = UUID.randomUUID();

    @AfterEach
    void apagar() {
        proveedor.close();
    }

    @Test
    @DisplayName("Transcribe: manda el audio con el modelo y la pista, y cobra el minuto (~10 pesos)")
    void transcribe() {
        proveedor.responde(200, "{\"text\":\"Trabajamos past simple y le costó used to.\"}");

        assertThat(transcriptor.transcribir(maria, new byte[] {1, 2, 3}, "audio/webm;codecs=opus", 60))
                .contains("Trabajamos past simple y le costó used to.");
        assertThat(proveedor.cuerpos().getFirst()).contains("gpt-4o-mini-transcribe")
                .contains("español, con palabras").contains("dictado.webm");
        verify(presupuesto).registrarAudio(eq(maria), eq("gpt-4o-mini-transcribe"), eq(60), eq(10L), anyInt(), eq("OK"));
    }

    @Test
    @DisplayName("Sin texto: vacío, para que el profesor lo escriba, y su fila INVALID_OUTPUT")
    void sinTexto() {
        proveedor.responde(200, "{\"text\":\"  \"}");

        assertThat(transcriptor.transcribir(maria, new byte[] {1}, "audio/mp4", 5)).isEmpty();
        verify(presupuesto).registrarAudio(eq(maria), eq("gpt-4o-mini-transcribe"), eq(5), eq(1L), anyInt(),
                eq("INVALID_OUTPUT"));
    }

    @Test
    @DisplayName("Lento: se corta y la fila dice TIMEOUT")
    void lento() {
        proveedor.tarda(2_500).responde(200, "{\"text\":\"tarde\"}");

        assertThat(transcriptor.transcribir(maria, new byte[] {1}, "audio/webm", 30)).isEmpty();
        verify(presupuesto).registrarAudio(eq(maria), eq("gpt-4o-mini-transcribe"), eq(30), eq(5L), anyInt(), eq("TIMEOUT"));
    }

    @Test
    @DisplayName("Sin llave no se llama a nadie")
    void sinLlave() {
        OpenAiTranscriptorDeDictado sinLlave = new OpenAiTranscriptorDeDictado("", "m", proveedor.url("/x"), 0.003, 3101,
                1, presupuesto);

        assertThat(sinLlave.transcribir(maria, new byte[] {1}, "audio/webm", 10)).isEmpty();
        assertThat(proveedor.llamadas()).isZero();
        verify(presupuesto, org.mockito.Mockito.never()).registrarAudio(isNull(), isNull(), anyInt(), anyInt(), anyInt(), isNull());
    }
}
