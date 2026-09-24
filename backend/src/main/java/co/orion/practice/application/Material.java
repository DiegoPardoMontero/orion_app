package co.orion.practice.application;

import java.util.List;

/**
 * Lo que el acta decía al publicarse, que es de donde salen los ejercicios: todo ejercicio se
 * ancla aquí (brief, paso B2). Se guarda con el set porque la generación es diferida y el acta se
 * puede corregir después.
 */
public record Material(String languageCode, String workedOn, String recurringIssues, String nextSteps,
                       List<Termino> vocabulary, String bookingId, String classStartsAt,
                       String studentLevel, String studentGoal) {

    /** Sin nivel ni objetivo: lo que sale del acta, antes de mirar la ficha del estudiante. */
    public Material(String languageCode, String workedOn, String recurringIssues, String nextSteps,
                    List<Termino> vocabulary, String bookingId, String classStartsAt) {
        this(languageCode, workedOn, recurringIssues, nextSteps, vocabulary, bookingId, classStartsAt, null, null);
    }

    /** D7 (Pardo, 23/09/2026): el nivel que declara y su objetivo, para ajustar los ejercicios. */
    public Material conEstudiante(String nivel, String objetivo) {
        return new Material(languageCode, workedOn, recurringIssues, nextSteps, vocabulary, bookingId, classStartsAt,
                nivel, objetivo);
    }

    public record Termino(String term, String meaning) {
    }

    public List<Termino> vocabulary() {
        return vocabulary == null ? List.of() : vocabulary;
    }
}
