package co.orion.assessment.api;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * @param languageCode qué idioma se evalúa. Nulo: el único activo, que hoy es inglés.
 * @param goals        lo que la persona quiere hacer con el idioma. Alimenta las recomendaciones,
 *                     no el puntaje: el objetivo no hace a nadie hablar mejor ni peor.
 */
public record StartAssessmentRequest(@Size(max = 5) String languageCode, List<String> goals) {
}
