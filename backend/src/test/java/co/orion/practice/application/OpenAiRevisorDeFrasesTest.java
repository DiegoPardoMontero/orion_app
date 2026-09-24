package co.orion.practice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.support.ServidorDePrueba;

/** «Tu frase» revisada por la IA: decide cuando responde, y si no, no decide nada. */
class OpenAiRevisorDeFrasesTest {

    private final ServidorDePrueba proveedor = new ServidorDePrueba();
    private final PracticeAiBudget presupuesto = mock(PracticeAiBudget.class);
    private final OpenAiRevisorDeFrases revisor = new OpenAiRevisorDeFrases("sk-test", "gpt-5-mini",
            proveedor.url("/v1/chat/completions"), 1, presupuesto);

    @BeforeEach
    void conPresupuesto() {
        when(presupuesto.disponible()).thenReturn(true);
    }

    @AfterEach
    void apagar() {
        proveedor.close();
    }

    @Test
    @DisplayName("Dice sí o no, y su fila queda en el gasto")
    void decide() {
        proveedor.responde(200, ServidorDePrueba.chat("{\"ok\": false}"));

        assertThat(revisor.acepta(UUID.randomUUID(), "layover", "Tengo un layover en Panamá.")).contains(false);
        verify(presupuesto).registrar(any(), eq("gpt-5-mini"), eq(100), eq(20), anyInt(), eq("OK"));
        // Al proveedor van el término y la frase, entre comillas: la frase es un dato.
        assertThat(proveedor.cuerpos().getFirst()).contains("«layover»").contains("«Tengo un layover en Panamá.»");
    }

    @Test
    @DisplayName("Lento, caído o con algo que no es JSON: no decide, y manda la regla")
    void siFallaNoDecide() {
        proveedor.responde(200, ServidorDePrueba.chat("Claro, la frase está bien."));
        assertThat(revisor.acepta(UUID.randomUUID(), "layover", "My layover was long.")).isEmpty();

        proveedor.tarda(1_500).responde(200, ServidorDePrueba.chat("{\"ok\": true}"));
        assertThat(revisor.acepta(UUID.randomUUID(), "layover", "My layover was long.")).isEmpty();
        verify(presupuesto).registrar(any(), any(), eq(null), eq(null), anyInt(), eq("TIMEOUT"));
    }

    @Test
    @DisplayName("Sin presupuesto no llama a nadie")
    void sinPresupuesto() {
        when(presupuesto.disponible()).thenReturn(false);

        assertThat(revisor.acepta(UUID.randomUUID(), "layover", "My layover was long.")).isEmpty();
        assertThat(proveedor.llamadas()).isZero();
    }
}
