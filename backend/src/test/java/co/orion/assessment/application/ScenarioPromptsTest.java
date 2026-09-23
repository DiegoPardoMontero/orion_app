package co.orion.assessment.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El guion tal como le llega al modelo, no tal como está en el archivo.
 *
 * <p>La v3 le hablaba al modelo en español y le pedía conversar en inglés en la segunda frase: los
 * saludos salían en español, la persona contestaba en español y el diagnóstico acababa en la rama
 * en español sin que nadie lo hubiera elegido. Lo que protege esto es que la regla del idioma
 * sobreviva al procesado —que es donde se pierden los títulos, por ejemplo— y que sea lo primero.
 */
class ScenarioPromptsTest {

    private final ScenarioPrompts prompts = new ScenarioPrompts();

    @Test
    @DisplayName("Lo primero que lee el modelo es en qué idioma hablar, y en ese idioma")
    void elIdiomaVaPrimero() {
        String guion = prompts.escenario("EN", 2, "Ana");

        assertThat(guion).startsWith("**LANGUAGE: the rule above every other rule.**");
        assertThat(guion).contains("You speak **English**.");
    }

    @Test
    @DisplayName("El nombre del idioma en inglés sigue al idioma pedido")
    void elNombreSigueAlIdioma() {
        assertThat(prompts.escenario("FR", 2, "Ana")).contains("You speak **French**.");
        assertThat(prompts.escenario(null, 2, "Ana")).contains("You speak **English**.");
    }

    @Test
    @DisplayName("No viaja ningún marcador sin rellenar ni ninguna nota para nosotros")
    void nadaSinRellenar() {
        String guion = prompts.escenario("EN", 2, "Ana");

        assertThat(guion).doesNotContain("{{").doesNotContain("}}");
        assertThat(guion.lines()).noneMatch(linea -> linea.startsWith("#"));
        assertThat(guion).contains("Hi Ana!");
    }

    @Test
    @DisplayName("La versión que se registra es la del archivo vigente")
    void laVersionEsLaVigente() {
        assertThat(prompts.versionVigente()).isEqualTo("assessment-scenario-v5");
    }
}
