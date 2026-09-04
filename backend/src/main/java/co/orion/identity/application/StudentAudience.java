package co.orion.identity.application;

import java.util.UUID;

/**
 * ¿Este profesor tiene relación con este estudiante? Es lo que decide si puede ver su perfil.
 *
 * <p>Es un puerto y no una llamada directa porque la respuesta vive en dos módulos —una reserva en
 * {@code scheduling}, una conversación en {@code messaging}— y {@code identity} no puede importar
 * ninguno de los dos sin cerrar un ciclo: los dos importan {@code identity}. La implementación está
 * en {@code messaging}, que es el módulo que ya responde esta misma pregunta para abrir un hilo.
 */
public interface StudentAudience {

    /** Han compartido al menos una clase (en cualquier estado) o tienen conversación abierta. */
    boolean professorHasRelationWith(UUID professorId, UUID studentId);
}
