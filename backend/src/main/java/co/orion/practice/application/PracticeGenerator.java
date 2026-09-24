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

    /**
     * El proveedor no respondió: se agotó el tiempo, un 5xx, un 429, una llave revocada. No es culpa
     * del acta, así que el set no gasta un intento: sigue pendiente para la siguiente corrida.
     */
    final class ProveedorNoRespondio extends RuntimeException {
        public ProveedorNoRespondio(String motivo) {
            super(motivo);
        }
    }

    /**
     * Un ejercicio tal como sale del generador; {@code payload} y {@code expected} en JSON o texto.
     *
     * @param pista lo que se muestra en el «Casi…»: ayuda a acertar sin dar la respuesta. La
     *              explicación, en cambio, se muestra al cerrar el ejercicio (24/09/2026, diseño).
     */
    record Generado(PracticeItemType tipo, String prompt, String payload, String expected, String explicacion,
                    String terminoFuente, String pista) {

        /** Sin pista: el «Casi…» muestra la explicación, como antes. */
        public Generado(PracticeItemType tipo, String prompt, String payload, String expected, String explicacion,
                 String terminoFuente) {
            this(tipo, prompt, payload, expected, explicacion, terminoFuente, null);
        }
    }
}
