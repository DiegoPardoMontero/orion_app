package co.orion.teaching.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quién convierte las notas del profesor en un acta. Detrás de interfaz, como
 * {@code MeetingLinkProvider} y {@code PaymentProvider}: cambiar de proveedor no toca el dominio, y
 * ningún test depende de una llamada de red.
 *
 * <p>Vacío cuando no puede —apagado, sin presupuesto, caído, salida inválida— y entonces el
 * profesor escribe a mano en los mismos cuatro campos. <strong>La IA es una mejora del camino,
 * nunca el camino.</strong>
 */
public interface LessonNoteDrafter {

    Optional<Borrador> redactar(Contexto contexto);

    /** Lo único que ve el proveedor del estudiante: nombre de pila, idioma, nivel y objetivo (D7). */
    record Contexto(UUID actorId, String notas, String nombreDePila, String idioma, String nivel,
                    String objetivo) {
    }

    record Borrador(String workedOn, String recurringIssues, String nextSteps, List<Palabra> vocabulario,
                    String promptVersion) {
    }

    record Palabra(String term, String meaning) {
    }
}
