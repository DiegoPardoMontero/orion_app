package co.orion.teaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import co.orion.catalog.application.PlatformSettingsService;

/**
 * El redactor contra un proveedor que se porta mal, en un servidor local (brief, paso C2): tarda
 * más que el corte, devuelve algo que no es JSON, o responde bien. Toda llamada deja su fila en el
 * registro de gasto, termine como termine, y cuando no hay borrador el servicio cae al camino a
 * mano —eso lo prueba {@code LessonNoteIT}—.
 */
class OpenAiLessonNoteDrafterRedTest {

    private static final String BUENO = """
            {"workedOn":"Past simple.","recurringIssues":"Dice 'I go yesterday'.","nextSteps":"Condicionales.",
             "vocabulary":[{"term":"used to","meaning":"solía"}]}""";

    private HttpServer servidor;
    private final AtomicInteger llamadas = new AtomicInteger();
    private volatile int esperaMs;
    private volatile String contenido = BUENO;

    private final TeachingAiBudget presupuesto = mock(TeachingAiBudget.class);
    private final PlatformSettingsService settings = mock(PlatformSettingsService.class);
    private final LessonNoteDrafter.Contexto contexto =
            new LessonNoteDrafter.Contexto(null, "notas de la clase de hoy, past simple", "Ana", "inglés", null, null);

    @BeforeEach
    void levantar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/v1/chat/completions", intercambio -> {
            llamadas.incrementAndGet();
            try {
                Thread.sleep(esperaMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            String cuerpo = """
                    {"choices":[{"message":{"content":"%s"}}],"usage":{"prompt_tokens":300,"completion_tokens":120}}"""
                    .formatted(enJson(contenido));
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        when(presupuesto.disponible()).thenReturn(true);
        when(settings.getInt("ai_note_timeout_seconds")).thenReturn(1);
    }

    /** Lo justo para meter un texto dentro de una cadena JSON. */
    private static String enJson(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
    }

    private OpenAiLessonNoteDrafter redactor() {
        return new OpenAiLessonNoteDrafter("sk-test", "gpt-5-mini", presupuesto, settings,
                "http://localhost:" + servidor.getAddress().getPort() + "/v1/chat/completions");
    }

    @Test
    @DisplayName("Responde bien: hay borrador y su fila OK")
    void respondeBien() {
        assertThat(redactor().redactar(contexto)).isPresent();
        assertThat(llamadas.get()).isEqualTo(1);
        verify(presupuesto).registrar(isNull(), eq("gpt-5-mini"), eq(300), eq(120), anyInt(), eq("OK"));
    }

    @Test
    @DisplayName("Tarda más que el corte: sin borrador, sin reintento, y su fila TIMEOUT")
    void tardaDemasiado() {
        esperaMs = 2_500;

        assertThat(redactor().redactar(contexto)).isEmpty();
        assertThat(llamadas.get()).isEqualTo(1);
        verify(presupuesto).registrar(isNull(), eq("gpt-5-mini"), isNull(), isNull(), anyInt(), eq("TIMEOUT"));
    }

    @Test
    @DisplayName("Devuelve algo que no es JSON: un reintento, luego nada, y dos filas INVALID_OUTPUT")
    void noEsJson() {
        contenido = "Claro, aquí tienes el acta de la clase:";

        assertThat(redactor().redactar(contexto)).isEmpty();
        assertThat(llamadas.get()).isEqualTo(2);
        verify(presupuesto, times(2)).registrar(any(), eq("gpt-5-mini"), eq(300), eq(120), anyInt(),
                eq("INVALID_OUTPUT"));
    }

    @Test
    @DisplayName("Con el presupuesto agotado no se llama a nadie")
    void sinPresupuesto() {
        when(presupuesto.disponible()).thenReturn(false);

        assertThat(redactor().redactar(contexto)).isEmpty();
        assertThat(llamadas.get()).isZero();
        verify(presupuesto, never()).registrar(any(), any(), any(), any(), anyInt(), any());
    }
}
