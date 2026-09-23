package co.orion.assessment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdiomaDeLaFraseTest {

    @Test
    @DisplayName("Las frases de la rama en español se reconocen, con tilde o sin ella")
    void espanol() {
        assertThat(IdiomaDeLaFrase.pareceEspanol("Sigamos en español, que así me cuentas mejor.")).isTrue();
        assertThat(IdiomaDeLaFrase.pareceEspanol("¿Y para que lo necesitas en el trabajo?")).isTrue();
        assertThat(IdiomaDeLaFrase.pareceEspanol("Cuentame que es lo que mas te gusta de la ciudad")).isTrue();
    }

    @Test
    @DisplayName("El inglés de Meissa no se confunde, ni siquiera cuando dice «Orión»")
    void ingles() {
        assertThat(IdiomaDeLaFrase.pareceEspanol("Hi Ana! I'm Meissa, from Orión.")).isFalse();
        assertThat(IdiomaDeLaFrase.pareceEspanol("What's the hardest bug you've fixed this month?")).isFalse();
        assertThat(IdiomaDeLaFrase.pareceEspanol("In Orión you'd practise those meetings with a teacher from your field.")).isFalse();
        assertThat(IdiomaDeLaFrase.pareceEspanol("")).isFalse();
    }

    @Test
    @DisplayName("Un nombre o un lugar con tilde no vuelve español al inglés")
    void nombresConTilde() {
        assertThat(IdiomaDeLaFrase.pareceEspanol("What's the traffic like in Bogotá today?")).isFalse();
        assertThat(IdiomaDeLaFrase.pareceEspanol("Tell me more, Sofía.")).isFalse();
        assertThat(IdiomaDeLaFrase.pareceEspanol("Sofía, what do you do at the hospital?")).isFalse();
        assertThat(IdiomaDeLaFrase.pareceEspanol("So you moved from Medellín to Cali, Andrés?")).isFalse();
        assertThat(IdiomaDeLaFrase.pareceEspanol("She sounds genial, and the vale near Bogotá is lovely.")).isFalse();
        // Y el español con nombres sigue siendo español, también el corto que empieza como un vocativo.
        assertThat(IdiomaDeLaFrase.pareceEspanol("Sí, claro.")).isTrue();
        assertThat(IdiomaDeLaFrase.pareceEspanol("Hola, Sofía.")).isTrue();
        assertThat(IdiomaDeLaFrase.pareceEspanol("Ajá, sigue.")).isTrue();
        assertThat(IdiomaDeLaFrase.pareceEspanol("Sofía, cuéntame qué haces en el hospital.")).isTrue();
        assertThat(IdiomaDeLaFrase.pareceEspanol("¿Y cómo es el tráfico en Bogotá?")).isTrue();
    }
}
