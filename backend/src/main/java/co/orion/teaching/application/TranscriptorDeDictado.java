package co.orion.teaching.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Pasa a texto lo que el profesor dictó sobre su clase. El texto cae en la caja del acta y el
 * profesor lo revisa antes de generar nada: el dictado reemplaza al teclado, no al profesor.
 *
 * <p>Vacío cuando no se entendió nada o el proveedor falló: el profesor lo escribe a mano.
 */
public interface TranscriptorDeDictado {

    Optional<String> transcribir(UUID profesorId, byte[] audio, String tipo, int segundos);
}
