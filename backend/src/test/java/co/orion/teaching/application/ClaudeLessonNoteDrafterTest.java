package co.orion.teaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.catalog.application.PlatformSettingsService;
import co.orion.shared.ai.ClaudeTexto;
import co.orion.support.ServidorDePrueba;
import co.orion.teaching.application.LessonNoteDrafter.Borrador;
import co.orion.teaching.application.LessonNoteDrafter.Contexto;

/** El acta con Claude Opus contra un proveedor de mentira: el JSON (aun con cerca), el reintento, el corte. */
class ClaudeLessonNoteDrafterTest {

    private static final String ACTA = "{\"workedOn\":\"Past simple con ejemplos de su semana.\","
            + "\"recurringIssues\":\"Dice «I go yesterday».\",\"nextSteps\":\"Contar su fin de semana en pasado.\","
            + "\"vocabulary\":[{\"term\":\"used to\",\"meaning\":\"solía\"}]}";

    private final ServidorDePrueba proveedor = new ServidorDePrueba();
    private final TeachingAiBudget presupuesto = mock(TeachingAiBudget.class);
    private final PlatformSettingsService settings = mock(PlatformSettingsService.class);
    private final ClaudeTexto claude = new ClaudeTexto("sk-ant-test", proveedor.url(""), "claude-opus-5-5", "claude-haiku-5-5");
    private final ClaudeLessonNoteDrafter drafter = new ClaudeLessonNoteDrafter(claude, presupuesto, settings);

    private final Contexto contexto = new Contexto(UUID.randomUUID(),
            "Trabajamos past simple; sigue diciendo 'I go yesterday'. Vocabulario: 'used to' (solía).",
            "Ana", "inglés", "BEGINNER", "viajar");

    @AfterEach
    void apagar() {
        proveedor.close();
    }

    private void listo() {
        when(presupuesto.disponible()).thenReturn(true);
        when(settings.getInt(anyString())).thenReturn(2);
    }

    @Test
    @DisplayName("El JSON válido se vuelve borrador, con Opus y esfuerzo bajo")
    void redacta() {
        listo();
        proveedor.responde(200, ServidorDePrueba.claude(ACTA));

        Optional<Borrador> borrador = drafter.redactar(contexto);

        assertThat(borrador).isPresent();
        assertThat(borrador.get().workedOn()).startsWith("Past simple");
        assertThat(borrador.get().vocabulario()).singleElement().satisfies(p -> assertThat(p.term()).isEqualTo("used to"));
        assertThat(proveedor.cuerpos().getFirst()).contains("\"model\":\"claude-opus-5-5\"").contains("\"effort\":\"low\"")
                .doesNotContain("\"type\":\"disabled\"");
    }

    @Test
    @DisplayName("Si el modelo envuelve el JSON en una cerca de código, igual se lee")
    void conCerca() {
        listo();
        proveedor.responde(200, ServidorDePrueba.claude("Aquí va:\n```json\n" + ACTA + "\n```"));

        assertThat(drafter.redactar(contexto)).isPresent();
    }

    @Test
    @DisplayName("Una salida inválida se reintenta una vez y no más")
    void reintentaUnaVez() {
        listo();
        proveedor.responde(200, ServidorDePrueba.claude("Lo siento, no puedo armar el acta con estas notas."));

        assertThat(drafter.redactar(contexto)).isEmpty();
        assertThat(proveedor.llamadas()).isEqualTo(2);
    }

    @Test
    @DisplayName("Lento: se corta, vacío, sin reintento")
    void lento() {
        listo();
        proveedor.tarda(3_000).responde(200, ServidorDePrueba.claude(ACTA));

        assertThat(drafter.redactar(contexto)).isEmpty();
        assertThat(proveedor.llamadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin presupuesto no llama")
    void sinPresupuesto() {
        when(presupuesto.disponible()).thenReturn(false);

        assertThat(drafter.redactar(contexto)).isEmpty();
        assertThat(proveedor.llamadas()).isZero();
    }
}
