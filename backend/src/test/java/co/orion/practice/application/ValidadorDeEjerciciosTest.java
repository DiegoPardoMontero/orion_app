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
    @DisplayName("Un ejercicio de vocabulario con un término que el acta no tiene se descarta")
    void sinAnclaSeDescarta() {
        assertThat(ValidadorDeEjercicios.validos(List.of(hueco("used to"), hueco("gonna")), ACTA, 4))
                .extracting(Generado::terminoFuente).containsExactly("used to");
    }

    @Test
    @DisplayName("Tipos variados: como mucho dos del mismo tipo")
    void comoMuchoDosIguales() {
        assertThat(ValidadorDeEjercicios.validos(
                List.of(hueco("used to"), hueco("deadline"), hueco("used to")), ACTA, 4)).hasSize(2);
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
    @DisplayName("Un ejercicio mal formado se descarta él solo, no el set entero")
    void malFormado() {
        Generado roto = new Generado(PracticeItemType.ORDER_DIALOGUE, "Ordena.", "{no es json", "[]", "x", null);
        Generado escribir = new Generado(PracticeItemType.WRITE_SENTENCE, "Escribe.", "{\"term\":\"deadline\"}",
                null, "Usa la palabra.", "deadline");

        assertThat(ValidadorDeEjercicios.validos(List.of(roto, escribir), ACTA, 4)).containsExactly(escribir);
    }
}
