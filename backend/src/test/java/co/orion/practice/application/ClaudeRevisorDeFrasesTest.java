package co.orion.practice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import co.orion.shared.ai.ClaudeTexto;
import co.orion.support.ServidorDePrueba;

class ClaudeRevisorDeFrasesTest {

    private final ServidorDePrueba proveedor = new ServidorDePrueba();
    private final PracticeAiBudget presupuesto = mock(PracticeAiBudget.class);
    private final ClaudeRevisorDeFrases revisor = new ClaudeRevisorDeFrases(
            new ClaudeTexto("sk-ant-test", proveedor.url(""), "claude-opus-5-5", "claude-haiku-5-5"), presupuesto);

    @AfterEach
    void apagar() {
        proveedor.close();
    }

    @Test
    void aceptaYRechazaSegunElJson() {
        when(presupuesto.disponible()).thenReturn(true);
        proveedor.responde(200, ServidorDePrueba.claude("{\"ok\": true}"));
        assertThat(revisor.acepta(UUID.randomUUID(), "FR", "escale", "J'ai fait une escale à Paris.")).contains(true);
        assertThat(proveedor.cuerpos().getFirst()).contains("profesor de franc").contains("\"model\":\"claude-haiku-5-5\"");

        proveedor.responde(200, ServidorDePrueba.claude("{\"ok\": false}"));
        assertThat(revisor.acepta(UUID.randomUUID(), "EN", "layover", "Tengo una escala")).contains(false);
    }

    @Test
    void sinRespuestaMandaLaRegla() {
        when(presupuesto.disponible()).thenReturn(true);
        proveedor.responde(500, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}");
        assertThat(revisor.acepta(UUID.randomUUID(), "EN", "layover", "My layover was long")).isEmpty();
    }
}
