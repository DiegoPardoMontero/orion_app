package co.orion.practice.application;

import java.util.List;
import java.util.UUID;

import co.orion.practice.domain.PracticeItemType;

/**
 * Genera los ejercicios de un set a partir del acta. La salida pasa por {@link ValidadorDeEjercicios}
 * antes de guardarse: lo que no se ancla al acta se descarta.
 */
public interface PracticeGenerator {

    List<Generado> generar(UUID estudianteId, Material material, int cuantos);

    /** Si hoy se puede generar. Sin presupuesto, los sets esperan a mañana en vez de fallar. */
    default boolean disponible() {
        return true;
    }

    /** Un ejercicio tal como sale del generador; {@code payload} y {@code expected} en JSON o texto. */
    record Generado(PracticeItemType tipo, String prompt, String payload, String expected, String explicacion,
                    String terminoFuente) {
    }
}
