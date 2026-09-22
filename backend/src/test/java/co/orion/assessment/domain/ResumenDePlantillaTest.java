package co.orion.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La frase de respaldo cuando la IA no escribe el resumen: cierta para cualquiera. */
class ResumenDePlantillaTest {

    @Test
    @DisplayName("Nombra sus objetivos solo si los marcó, y en minúscula dentro de la frase")
    void conObjetivos() {
        assertThat(ResumenDePlantilla.para("Ana", List.of("Trabajo", "Viajes")))
                .isEqualTo("Gracias por conversar con Meissa, Ana. Con lo que nos contaste, vemos "
                        + "muchas posibilidades de que Orión te ayude a avanzar en lo que buscas: "
                        + "trabajo y viajes.");
        assertThat(ResumenDePlantilla.para("Ana", List.of("Trabajo", "Viajes", "Estudio")))
                .endsWith("trabajo, viajes y estudio.");
    }

    @Test
    @DisplayName("Sin objetivos ni nombre sigue diciendo algo cierto")
    void sinNada() {
        assertThat(ResumenDePlantilla.para(null, List.of()))
                .startsWith("Gracias por conversar con Meissa. ")
                .endsWith("hablar inglés con más confianza.");
    }
}
