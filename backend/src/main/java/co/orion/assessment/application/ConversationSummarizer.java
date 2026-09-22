package co.orion.assessment.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quién escribe el resumen personalizado de la conversación: lo que la persona contó y por qué Orión
 * le sirve para eso.
 *
 * <p>Detrás de interfaz, como {@link VoiceConversationProvider}: cambiar de proveedor no toca el
 * dominio, y los tests no dependen de la red. Devuelve vacío cuando no puede —proveedor caído,
 * salida que no pasa la revisión, sin proveedor configurado— y quien lo llama cae a
 * {@code ResumenDePlantilla}. <strong>El resumen nunca bloquea el resultado.</strong>
 */
public interface ConversationSummarizer {

    Optional<String> resumir(Pedido pedido);

    /**
     * @param actorId      a quién se le carga el gasto
     * @param nombreDePila solo el nombre de pila: el proveedor no necesita más
     * @param objetivos    los objetivos que marcó antes de empezar, con su nombre en español
     * @param turnos       la conversación entera, en orden
     */
    record Pedido(UUID actorId, String nombreDePila, List<String> objetivos, List<Turno> turnos) {
    }

    /** @param deMeissa si lo dijo Meissa; si no, lo dijo la persona */
    record Turno(boolean deMeissa, String texto) {
    }
}
