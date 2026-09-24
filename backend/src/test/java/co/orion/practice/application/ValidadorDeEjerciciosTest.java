package co.orion.practice.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.orion.practice.application.PracticeGenerator.Generado;
import co.orion.practice.domain.PracticeItemType;

class ValidadorDeEjerciciosTest {

    private static final Material ACTA = new Material("EN", "Talked about childhood routines.",
            "Says 'I go yesterday'.", "Conditionals.",
            List.of(new Material.Termino("used to", "solía"), new Material.Termino("deadline", "fecha límite")),
            null, null);

    private static Generado hueco(String termino) {
        return new Generado(PracticeItemType.FILL_BLANK, "Completa.",
                "{\"sentence\":\"I ___ swim.\",\"options\":[\"" + termino + "\",\"use to\"]}", termino,
                "Aquí va la palabra de clase.", termino);
    }

    @Test
    @DisplayName("Un diálogo dice quién habla, y en el orden esperado nadie habla dos veces seguidas")
    void losTurnosSeAlternan() {
        assertThat(ValidadorDeEjercicios.seTurnan(List.of("Interviewer: Tell me about a project.",
                "Candidate: I led a migration.", "Interviewer: What was hard?", "Candidate: The deadline."))).isTrue();
        // Lo que devolvió OpenAI con el prompt v3: dos preguntas juntas y las dos respuestas después.
        assertThat(ValidadorDeEjercicios.seTurnan(List.of("Interviewer: Tell me about a project.",
                "Interviewer: What was hard?", "Candidate: The deadline.", "Candidate: I led a migration."))).isFalse();
        // Sin etiquetas no se puede comprobar, y no pasa.
        assertThat(ValidadorDeEjercicios.seTurnan(List.of("Hi, how was the trip?", "Great, thanks!", "Where did you go?"))).isFalse();
    }

    @Test
    @DisplayName("Un ejercicio de vocabulario con un término que el acta no tiene se descarta")
    void sinAnclaSeDescarta() {
        assertThat(ValidadorDeEjercicios.validos(List.of(hueco("used to"), hueco("gonna")), ACTA, 4))
                .extracting(Generado::terminoFuente).containsExactly("used to");
    }

    @Test
    @DisplayName("Cada ejercicio de un tipo distinto: dos huecos no son dos ejercicios")
    void unoDeCadaTipo() {
        assertThat(ValidadorDeEjercicios.validos(
                List.of(hueco("used to"), hueco("deadline"), hueco("used to")), ACTA, 4)).hasSize(1);
    }

    private static Generado dictado(String frase) {
        return new Generado(PracticeItemType.DICTATION, "Escucha.", "{\"say\":\"" + frase + "\"}", frase, "x", null);
    }

    private static Generado frasePropia() {
        return new Generado(PracticeItemType.WRITE_SENTENCE, "Escribe.", "{\"term\":\"deadline\"}", null, "x", "deadline");
    }

    private static Generado responder() {
        return new Generado(PracticeItemType.CHOOSE_REPLY, "Responde.",
                "{\"from\":\"Mom\",\"message\":\"What did you do as a kid?\",\"options\":[\"I used to swim.\",\"I use to swim.\",\"I swimming.\"]}",
                "I used to swim.", "x", null);
    }

    private static Generado errorACazar() {
        return new Generado(PracticeItemType.SPOT_ERROR, "Caza el error.",
                "{\"tokens\":[\"I\",\"go\",\"yesterday\"]}", "{\"index\":1,\"correction\":\"I went yesterday.\"}", "x", null);
    }

    @Test
    @DisplayName("Primero uno de cada categoría, y el set sale en el orden de las categorías")
    void unoPorCategoriaYEnOrden() {
        // Dos de «frases» y uno de cada otra; con cuatro cupos, el segundo de «frases» se queda afuera.
        Generado armar = new Generado(PracticeItemType.BUILD_SENTENCE, "Arma.",
                "{\"tiles\":[\"swim\",\"I\",\"used to\"],\"guide\":\"Yo solía nadar\"}",
                "[\"I\",\"used to\",\"swim\"]", "x", null);
        var elegidos = ValidadorDeEjercicios.validos(
                List.of(frasePropia(), errorACazar(), armar, dictado("I used to swim"), responder(), hueco("deadline")), ACTA, 5);

        assertThat(elegidos).extracting(Generado::tipo).containsExactly(PracticeItemType.FILL_BLANK,
                PracticeItemType.SPOT_ERROR, PracticeItemType.CHOOSE_REPLY, PracticeItemType.DICTATION,
                PracticeItemType.WRITE_SENTENCE);
    }

    @Test
    @DisplayName("Los tipos nuevos se anclan al acta y tienen que poder resolverse")
    void losTiposNuevos() {
        // Escuchar y escribir: la frase tiene un término de la clase y es corta.
        assertThat(ValidadorDeEjercicios.validos(List.of(dictado("Here is my boarding pass")), ACTA, 5)).isEmpty();
        assertThat(ValidadorDeEjercicios.validos(List.of(dictado("I used to swim every day")), ACTA, 5)).hasSize(1);
        // Cazar el error exige que el acta hable de uno, y un índice dentro de la frase.
        Material sinErrores = new Material("EN", ACTA.workedOn(), "", null, ACTA.vocabulary(), null, null);
        assertThat(ValidadorDeEjercicios.validos(List.of(errorACazar()), sinErrores, 5)).isEmpty();
        Generado fueraDeRango = new Generado(PracticeItemType.SPOT_ERROR, "Caza.", "{\"tokens\":[\"I\",\"go\",\"yesterday\"]}",
                "{\"index\":7,\"correction\":\"I went yesterday.\"}", "x", null);
        assertThat(ValidadorDeEjercicios.validos(List.of(fueraDeRango), ACTA, 5)).isEmpty();
        // Armar la frase: las mismas fichas, desordenadas.
        Generado enOrden = new Generado(PracticeItemType.BUILD_SENTENCE, "Arma.",
                "{\"tiles\":[\"I\",\"used to\",\"swim\"],\"guide\":\"Yo solía nadar\"}",
                "[\"I\",\"used to\",\"swim\"]", "x", null);
        assertThat(ValidadorDeEjercicios.validos(List.of(enOrden), ACTA, 5)).isEmpty();
        // Responder: la esperada está entre las opciones, y no hay opciones repetidas.
        Generado sinLaEsperada = new Generado(PracticeItemType.CHOOSE_REPLY, "Responde.",
                "{\"from\":\"Mom\",\"message\":\"Hi?\",\"options\":[\"A\",\"B\"]}", "C", "x", null);
        assertThat(ValidadorDeEjercicios.validos(List.of(sinLaEsperada), ACTA, 5)).isEmpty();
        // Escuchar y elegir: lo que suena es un término de la clase.
        Generado oir = new Generado(PracticeItemType.LISTEN_CHOOSE, "Escucha.",
                "{\"say\":\"deadline\",\"options\":[\"fecha límite\",\"solía\",\"escala\"]}", "fecha límite", "x", "deadline");
        Generado oirOtra = new Generado(PracticeItemType.LISTEN_CHOOSE, "Escucha.",
                "{\"say\":\"layover\",\"options\":[\"escala\",\"solía\"]}", "escala", "x", "layover");
        assertThat(ValidadorDeEjercicios.validos(List.of(oir), ACTA, 5)).hasSize(1);
        assertThat(ValidadorDeEjercicios.validos(List.of(oirOtra), ACTA, 5)).isEmpty();
    }

    @Test
    @DisplayName("Corregir exige que el acta hable de un error recurrente")
    void corregirSinErrorRecurrente() {
        Generado corregir = new Generado(PracticeItemType.FIX_SENTENCE, "Corrige.",
                "{\"sentence\":\"I go yesterday\"}", "I went yesterday", "Pasado de go: went.", null);
        Material sinErrores = new Material("EN", "x", null, null, ACTA.vocabulary(), null, null);

        assertThat(ValidadorDeEjercicios.validos(List.of(corregir), ACTA, 4)).hasSize(1);
        assertThat(ValidadorDeEjercicios.validos(List.of(corregir), sinErrores, 4)).isEmpty();
    }

    @Test
    @DisplayName("Unir significados: si el modelo parafrasea uno, no habría forma de acertar y se descarta")
    void unirConSignificadoParafraseado() {
        String payload = "{\"terms\":[\"used to\",\"deadline\"],\"meanings\":[\"solía\",\"fecha límite\"]}";
        Generado bien = new Generado(PracticeItemType.MATCH_MEANING, "Une.", payload,
                "{\"used to\":\"solía\",\"deadline\":\"Fecha límite.\"}", "Las dos de clase.", null);
        Generado parafraseado = new Generado(PracticeItemType.MATCH_MEANING, "Une.", payload,
                "{\"used to\":\"acostumbraba\",\"deadline\":\"fecha límite\"}", "Las dos de clase.", null);

        assertThat(ValidadorDeEjercicios.validos(List.of(bien), ACTA, 4)).hasSize(1);
        assertThat(ValidadorDeEjercicios.validos(List.of(parafraseado), ACTA, 4)).isEmpty();
    }

    @Test
    @DisplayName("Lo que no cabe en la tabla se descarta aquí, antes de tumbar el set al guardarlo")
    void terminoFuenteDemasiadoLargo() {
        Generado largo = new Generado(PracticeItemType.WRITE_SENTENCE, "Escribe.", "{\"term\":\"deadline\"}",
                null, "Usa la palabra.", "deadline " + "x".repeat(120));

        assertThat(ValidadorDeEjercicios.validos(List.of(largo), ACTA, 4)).isEmpty();
    }

    @Test
    @DisplayName("La pista entre paréntesis del acta no cuenta: «get used to (+ing)» ancla a «get used to»")
    void pistaEntreParentesis() {
        Material conPista = new Material("EN", "x", null, null,
                List.of(new Material.Termino("get used to (+ing)", "acostumbrarse a")), null, null);
        Generado escribir = new Generado(PracticeItemType.WRITE_SENTENCE, "Escribe.", "{\"term\":\"get used to\"}",
                null, "Usa la expresión.", "get used to");

        assertThat(ValidadorDeEjercicios.validos(List.of(escribir), conPista, 4)).hasSize(1);
    }

    /**
     * Anclar al acta admite la pista entre paréntesis, pero el Evaluador compara sin quitarla: si la
     * esperada la trae y las opciones no, ninguna opción sería correcta.
     */
    @Test
    @DisplayName("Con la pista entre paréntesis, solo pasa lo que el Evaluador puede dar por bueno")
    void pistaYEvaluador() {
        Material conPista = new Material("EN", "x", null, null,
                List.of(new Material.Termino("get used to (+ing)", "acostumbrarse a"),
                        new Material.Termino("deadline", "fecha límite")), null, null);
        String opciones = "{\"sentence\":\"I can't ___ waking up early.\",\"options\":[\"get used to\",\"used to\"]}";
        Generado huecoBien = new Generado(PracticeItemType.FILL_BLANK, "Completa.", opciones, "get used to",
                "Así se dice.", "get used to (+ing)");
        Generado huecoImposible = new Generado(PracticeItemType.FILL_BLANK, "Completa.", opciones,
                "get used to (+ing)", "Así se dice.", "get used to (+ing)");
        Generado unirImposible = new Generado(PracticeItemType.MATCH_MEANING, "Une.",
                "{\"terms\":[\"get used to (+ing)\",\"deadline\"],\"meanings\":[\"acostumbrarse a\",\"fecha límite\"]}",
                "{\"get used to\":\"acostumbrarse a\",\"deadline\":\"fecha límite\"}", "Las dos de clase.", null);

        assertThat(ValidadorDeEjercicios.validos(List.of(huecoBien), conPista, 4)).hasSize(1);
        assertThat(ValidadorDeEjercicios.validos(List.of(huecoImposible), conPista, 4)).isEmpty();
        assertThat(ValidadorDeEjercicios.validos(List.of(unirImposible), conPista, 4)).isEmpty();
    }

    @Test
    @DisplayName("Un ejercicio mal formado se descarta él solo, no el set entero")
    void malFormado() {
        Generado roto = new Generado(PracticeItemType.ORDER_DIALOGUE, "Ordena.", "{no es json", "[]", "x", null);
        Generado escribir = new Generado(PracticeItemType.WRITE_SENTENCE, "Escribe.", "{\"term\":\"deadline\"}",
                null, "Usa la palabra.", "deadline");

        assertThat(ValidadorDeEjercicios.validos(List.of(roto, escribir), ACTA, 4)).containsExactly(escribir);
    }
}
