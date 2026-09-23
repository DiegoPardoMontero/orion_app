package co.orion.practice.application;

import java.util.List;

/**
 * Lo que el acta decía al publicarse, que es de donde salen los ejercicios: todo ejercicio se
 * ancla aquí (brief, paso B2). Se guarda con el set porque la generación es diferida y el acta se
 * puede corregir después.
 */
public record Material(String languageCode, String workedOn, String recurringIssues, String nextSteps,
                       List<Termino> vocabulary, String bookingId, String classStartsAt) {

    public record Termino(String term, String meaning) {
    }

    public List<Termino> vocabulary() {
        return vocabulary == null ? List.of() : vocabulary;
    }
}
