package co.orion.practice.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Quién decide si la frase propia de «Tu frase» vale: una frase en inglés con sentido que usa el
 * término (o una flexión suya). Vacío si no hay quién revisar —sin IA, sin presupuesto o sin
 * respuesta—, y entonces manda la regla de {@code Evaluador}: usar el término en una frase de
 * cuatro palabras o más.
 */
public interface RevisorDeFrases {

    Optional<Boolean> acepta(UUID estudianteId, String termino, String frase);
}
