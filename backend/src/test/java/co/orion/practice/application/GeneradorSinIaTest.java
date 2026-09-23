package co.orion.practice.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.practice.domain.PracticeItemType;

class GeneradorSinIaTest {

    private final GeneradorSinIa generador = new GeneradorSinIa();

    @Test
    @DisplayName("Con significados: emparejar primero, y los demás alternan hueco y frase propia")
    void conSignificados() {
        Material acta = new Material("EN", "We practised used to and deadline at work.", null, null,
                List.of(new Material.Termino("used to", "solía"), new Material.Termino("deadline", "fecha límite")),
                null, null);

        var salida = ValidadorDeEjercicios.validos(generador.generar(UUID.randomUUID(), acta, 4), acta, 4);

        assertThat(salida).hasSize(4);
        assertThat(salida.getFirst().tipo()).isEqualTo(PracticeItemType.MATCH_MEANING);
        assertThat(salida).extracting(PracticeGenerator.Generado::tipo)
                .contains(PracticeItemType.FILL_BLANK, PracticeItemType.WRITE_SENTENCE);
    }

    @Test
    @DisplayName("Sin vocabulario no hay nada que anclar: sale vacío y el set no se ofrecerá")
    void sinVocabulario() {
        Material acta = new Material("EN", "Conversación libre.", null, null, List.of(), null, null);

        assertThat(generador.generar(UUID.randomUUID(), acta, 4)).isEmpty();
    }
}
