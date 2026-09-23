package co.orion.practice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.practice.domain.PracticeItemType;
import co.orion.support.ServidorDePrueba;

/** El generador de práctica contra un proveedor de mentira. La validación la prueba su propio test. */
class OpenAiPracticeGeneratorTest {

    private static final Material ACTA = new Material("EN", "Past simple.", "Says 'I go yesterday'.", null,
            List.of(new Material.Termino("used to", "solía")), null, null);

    private final ServidorDePrueba proveedor = new ServidorDePrueba();
    private final PracticeAiBudget presupuesto = mock(PracticeAiBudget.class);
    private final OpenAiPracticeGenerator generador = new OpenAiPracticeGenerator("sk-test", "gpt-5-mini",
            proveedor.url("/v1/chat/completions"), 1, presupuesto);

    @AfterEach
    void apagar() {
        proveedor.close();
    }

    @Test
    @DisplayName("Lee los ejercicios del JSON, y un tipo que no existe se salta sin tumbar el resto")
    void leeLosEjercicios() {
        proveedor.responde(200, ServidorDePrueba.chat("""
                {"items":[{"type":"WRITE_SENTENCE","prompt":"Escribe.","payload":{"term":"used to"},"expected":null,
                           "explanation":"Usa la palabra.","sourceTerm":"used to"},
                          {"type":"INVENTADO","prompt":"x","payload":{},"expected":null,"explanation":"x"}]}"""));

        List<PracticeGenerator.Generado> generados = generador.generar(UUID.randomUUID(), ACTA, 4);

        assertThat(generados).singleElement().satisfies(g -> {
            assertThat(g.tipo()).isEqualTo(PracticeItemType.WRITE_SENTENCE);
            assertThat(g.payload()).contains("used to");
            assertThat(g.expected()).isNull();
        });
        verify(presupuesto).registrar(any(), eq("gpt-5-mini"), eq(100), eq(20), anyInt(), eq("OK"));
        // Al proveedor va el acta, nunca quién es el estudiante.
        assertThat(proveedor.cuerpos().getFirst()).contains("I go yesterday").contains("used to = solía");
    }

    @Test
    @DisplayName("Algo que no es JSON: nada, y su fila INVALID_OUTPUT")
    void noEsJson() {
        proveedor.responde(200, ServidorDePrueba.chat("Aquí tienes los ejercicios: ..."));

        assertThat(generador.generar(UUID.randomUUID(), ACTA, 4)).isEmpty();
        verify(presupuesto).registrar(any(), any(), eq(100), eq(20), anyInt(), eq("INVALID_OUTPUT"));
    }

    @Test
    @DisplayName("Lento: nada, y su fila TIMEOUT; el set se reintenta en la siguiente corrida")
    void lento() {
        proveedor.tarda(2_500).responde(200, ServidorDePrueba.chat("{\"items\":[]}"));

        assertThat(generador.generar(UUID.randomUUID(), ACTA, 4)).isEmpty();
        verify(presupuesto).registrar(any(), any(), eq(null), eq(null), anyInt(), eq("TIMEOUT"));
    }

    @Test
    @DisplayName("Sin presupuesto no está disponible: los sets esperan a mañana")
    void sinPresupuesto() {
        when(presupuesto.disponible()).thenReturn(false);

        assertThat(generador.disponible()).isFalse();
    }
}
