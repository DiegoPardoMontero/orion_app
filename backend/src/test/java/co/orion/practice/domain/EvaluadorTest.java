package co.orion.practice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluadorTest {

    @Test
    @DisplayName("Completar: cuenta la palabra, no las mayúsculas ni el punto")
    void completar() {
        String payload = "{\"sentence\":\"I ___ play football.\",\"options\":[\"used to\",\"use to\"]}";
        assertThat(Evaluador.esCorrecta(PracticeItemType.FILL_BLANK, payload, "used to", " Used to. ")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.FILL_BLANK, payload, "used to", "use to")).isFalse();
    }

    @Test
    @DisplayName("Corregir: vale la corrección esperada y las otras igual de válidas")
    void corregir() {
        String payload = "{\"sentence\":\"I go to the cinema yesterday\",\"accepted\":[\"Yesterday I went to the cinema\"]}";
        String esperado = "I went to the cinema yesterday.";
        assertThat(Evaluador.esCorrecta(PracticeItemType.FIX_SENTENCE, payload, esperado, "i went to the cinema yesterday")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.FIX_SENTENCE, payload, esperado, "Yesterday I went to the cinema.")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.FIX_SENTENCE, payload, esperado, "I go to the cinema yesterday")).isFalse();
    }

    @Test
    @DisplayName("Emparejar: todos los pares, en cualquier orden")
    void emparejar() {
        String esperado = "{\"used to\":\"solía\",\"deadline\":\"fecha límite\"}";
        assertThat(Evaluador.esCorrecta(PracticeItemType.MATCH_MEANING, "{}", esperado,
                "{\"deadline\":\"fecha límite\",\"used to\":\"solía\"}")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.MATCH_MEANING, "{}", esperado,
                "{\"deadline\":\"solía\",\"used to\":\"fecha límite\"}")).isFalse();
    }

    @Test
    @DisplayName("Ordenar: el orden exacto")
    void ordenar() {
        String esperado = "[\"Hi!\",\"Hi, how are you?\",\"Fine, thanks.\"]";
        assertThat(Evaluador.esCorrecta(PracticeItemType.ORDER_DIALOGUE, "{}", esperado,
                "[\"Hi!\",\"Hi, how are you?\",\"Fine, thanks.\"]")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.ORDER_DIALOGUE, "{}", esperado,
                "[\"Hi, how are you?\",\"Hi!\",\"Fine, thanks.\"]")).isFalse();
    }

    @Test
    @DisplayName("Escribir: una frase válida que nadie previó es correcta; sin el término o de dos palabras, no")
    void escribir() {
        String payload = "{\"term\":\"used to\"}";
        assertThat(Evaluador.esCorrecta(PracticeItemType.WRITE_SENTENCE, payload, null,
                "When I lived in Cali I used to swim every Sunday.")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.WRITE_SENTENCE, payload, null, "Used to.")).isFalse();
        assertThat(Evaluador.esCorrecta(PracticeItemType.WRITE_SENTENCE, payload, null,
                "I refused to go to the party yesterday.")).isFalse();
    }

    @Test
    @DisplayName("Escribir con un término que trae guion o una pista entre paréntesis también se puede acertar")
    void escribirConPuntuacionEnElTermino() {
        assertThat(Evaluador.esCorrecta(PracticeItemType.WRITE_SENTENCE, "{\"term\":\"check-in\"}", null,
                "I did the check-in at noon.")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.WRITE_SENTENCE, "{\"term\":\"T-shirt\"}", null,
                "My favourite T-shirt is blue.")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.WRITE_SENTENCE, "{\"term\":\"get used to (+ing)\"}", null,
                "You will get used to waking up early.")).isTrue();
        // Y sigue sin valer dentro de otra palabra ni con menos de cuatro palabras.
        assertThat(Evaluador.esCorrecta(PracticeItemType.WRITE_SENTENCE, "{\"term\":\"check-in\"}", null,
                "Check-in now.")).isFalse();
    }

    @Test
    @DisplayName("Una respuesta ilegible es incorrecta, no un error")
    void ilegible() {
        assertThat(Evaluador.esCorrecta(PracticeItemType.MATCH_MEANING, "{}", "{\"a\":\"b\"}", "no es json")).isFalse();
        assertThat(Evaluador.esCorrecta(PracticeItemType.FILL_BLANK, "{}", "used to", "  ")).isFalse();
    }

    @Test
    @DisplayName("Cazar el error: cuenta la ficha tocada; una respuesta que no es un índice no rompe nada")
    void cazarElError() {
        String esperado = "{\"index\":2,\"correction\":\"I have never been to Canada.\"}";
        assertThat(Evaluador.esCorrecta(PracticeItemType.SPOT_ERROR, "{}", esperado, "2")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.SPOT_ERROR, "{}", esperado, " 2 ")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.SPOT_ERROR, "{}", esperado, "1")).isFalse();
        assertThat(Evaluador.esCorrecta(PracticeItemType.SPOT_ERROR, "{}", esperado, "never")).isFalse();
    }

    @Test
    @DisplayName("Armar la frase: el orden de las fichas, sin mayúsculas ni puntos de más")
    void armarLaFrase() {
        String esperado = "[\"Where\",\"is\",\"my\",\"luggage\",\"?\"]";
        assertThat(Evaluador.esCorrecta(PracticeItemType.BUILD_SENTENCE, "{}", esperado,
                "[\"where\",\"is\",\"my\",\"luggage\",\"?\"]")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.BUILD_SENTENCE, "{}", esperado,
                "[\"Where\",\"my\",\"is\",\"luggage\",\"?\"]")).isFalse();
    }

    @Test
    @DisplayName("Responder en el chat y escuchar y elegir: la opción, sin mayúsculas ni el punto")
    void opciones() {
        assertThat(Evaluador.esCorrecta(PracticeItemType.CHOOSE_REPLY, "{}", "Yes, under García.", "yes, under García")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.LISTEN_CHOOSE, "{}", "escala", "Escala")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.LISTEN_CHOOSE, "{}", "escala", "equipaje")).isFalse();
    }

    @Test
    @DisplayName("Escuchar y escribir: se perdona una letra en una frase, no en una palabra corta")
    void dictado() {
        String frase = "Here is my boarding pass.";
        assertThat(Evaluador.esCorrecta(PracticeItemType.DICTATION, "{}", frase, "here is my boarding pass")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.DICTATION, "{}", frase, "Here is my boardin pass")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.DICTATION, "{}", frase, "Here is my bording pas")).isFalse();
        assertThat(Evaluador.esCorrecta(PracticeItemType.DICTATION, "{}", "tip", "top")).isFalse();
    }

    @Test
    @DisplayName("La contracción no es un error: I'm 30 vale por I am 30, y Here's por Here is")
    void contracciones() {
        String payload = "{\"sentence\":\"I have 30 years.\",\"accepted\":[\"I am 30 years old.\"]}";
        assertThat(Evaluador.esCorrecta(PracticeItemType.FIX_SENTENCE, payload, "I am 30.", "I'm 30")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.FIX_SENTENCE, payload, "I am 30.", "I'm 30 years old.")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.DICTATION, "{}", "Here is my boarding pass.",
                "Here's my boarding pass")).isTrue();
        assertThat(Evaluador.esCorrecta(PracticeItemType.FIX_SENTENCE, payload, "I am 30.", "I have 30")).isFalse();
    }
}
